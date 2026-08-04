#!/usr/bin/env python3
"""
TikTok Supreme Downloader - Video Only
Saves videos in the same folder as this script.
"""

import os
import sys
import re
import time
import subprocess
import importlib.util
from pathlib import Path
from typing import List, Optional
from datetime import datetime
import logging
from logging.handlers import RotatingFileHandler

# --- AUTO-INSTALL ---
def ensure_module(module_name: str, pip_name: str = None):
    if pip_name is None:
        pip_name = module_name
    spec = importlib.util.find_spec(module_name)
    if spec is None:
        print(f"⚡ {module_name} not found. Installing...")
        try:
            subprocess.check_call([sys.executable, "-m", "pip", "install", "--upgrade", pip_name])
            print(f"✅ {module_name} installed.")
        except Exception as e:
            print(f"❌ Failed: {e}")
            sys.exit(1)
    else:
        print(f"✅ {module_name} ready.")

ensure_module("yt_dlp", "yt-dlp")
import yt_dlp

# --- GET SCRIPT DIRECTORY ---
SCRIPT_DIR = Path(__file__).parent.absolute()

from blueknight_paths import download_dir

# --- LOGGER ---
class LogManager:
    def __init__(self, log_dir: str = "logs"):
        self.log_dir = SCRIPT_DIR / log_dir
        self.log_dir.mkdir(parents=True, exist_ok=True)
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        log_file = self.log_dir / f"tiktok_downloader_{timestamp}.log"
        
        self.logger = logging.getLogger('TikTokDownloader')
        self.logger.setLevel(logging.DEBUG)
        self.logger.handlers.clear()
        
        file_handler = RotatingFileHandler(log_file, maxBytes=10*1024*1024, backupCount=5, encoding='utf-8')
        file_handler.setLevel(logging.DEBUG)
        console_handler = logging.StreamHandler()
        console_handler.setLevel(logging.INFO)
        
        file_formatter = logging.Formatter('%(asctime)s | %(levelname)-8s | %(message)s', datefmt='%Y-%m-%d %H:%M:%S')
        file_handler.setFormatter(file_formatter)
        console_formatter = logging.Formatter('%(message)s')
        console_handler.setFormatter(console_formatter)
        
        self.logger.addHandler(file_handler)
        self.logger.addHandler(console_handler)
        self.log_file = log_file
        
        self.logger.info("="*80)
        self.logger.info(f"🐱 TIKTOK SUPREME - VIDEO ONLY")
        self.logger.info(f"📅 {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        self.logger.info(f"📁 Log: {log_file}")
        self.logger.info(f"📁 Save location: {download_dir('tiktok')}")
        self.logger.info("="*80)
    
    def info(self, msg): self.logger.info(msg)
    def debug(self, msg): self.logger.debug(msg)
    def warning(self, msg): self.logger.warning(msg)
    def error(self, msg): self.logger.error(msg)
    def download_start(self, url, idx=None, total=None):
        prefix = f"[{idx}/{total}] " if idx and total else ""
        self.logger.info(f"📥 {prefix}STARTING: {url}")
    def download_finish(self, url, path, size_mb, duration, idx=None, total=None):
        prefix = f"[{idx}/{total}] " if idx and total else ""
        self.logger.info(f"✅ {prefix}FINISHED: {Path(path).name} ({size_mb:.2f}MB, {duration:.1f}s)")
    def download_error(self, url, err, idx=None, total=None):
        prefix = f"[{idx}/{total}] " if idx and total else ""
        self.logger.error(f"❌ {prefix}FAILED: {url} - {err}")
    def get_log_path(self): return str(self.log_file)

# --- MAIN DOWNLOADER ---
class TikTokSupreme:
    def __init__(self):
        self.save_dir = download_dir("tiktok")
        self.log = LogManager()
        self.stats = {'total': 0, 'success': 0, 'failed': 0, 'total_size_mb': 0, 'total_duration': 0}
        self.log.info(f"📁 Videos will be saved to: {self.save_dir}")
    
    def _sanitize_filename(self, name: str) -> str:
        return re.sub(r'[<>:"/\\|?*]', '_', name).strip()
    
    def _get_ydl_opts(self, quality: str, output_template: str, progress_hook=None):
        if quality == 'best':
            format_spec = 'bv*+ba/b'
        elif quality == 'high':
            format_spec = 'bestvideo[height<=1080]+bestaudio/best[height<=1080]'
        elif quality == 'medium':
            format_spec = 'bestvideo[height<=720]+bestaudio/best[height<=720]'
        elif quality == 'low':
            format_spec = 'bestvideo[height<=480]+bestaudio/best[height<=480]'
        else:
            format_spec = 'best'
        
        ydl_opts = {
            'format': format_spec,
            'outtmpl': output_template,
            'quiet': True,
            'no_warnings': True,
            'ignoreerrors': True,
            'extract_flat': False,
            'merge_output_format': 'mp4',
            'postprocessors': [],
            'sleep_interval': 3,
            'max_sleep_interval': 7,
            'socket_timeout': 30,
            'retries': 10,
            'headers': {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
                'Accept-Language': 'en-US,en;q=0.9',
            },
            'progress_hooks': [progress_hook] if progress_hook else [],
        }
        
        return ydl_opts
    
    def _progress_hook(self, d):
        if d['status'] == 'downloading':
            if 'total_bytes' in d:
                total = d['total_bytes']
                downloaded = d.get('downloaded_bytes', 0)
                percent = (downloaded / total * 100) if total > 0 else 0
                speed = d.get('speed', 0)
                if speed:
                    speed_mb = speed / 1024 / 1024
                    self.log.debug(f"  ⏳ {percent:.1f}% | {speed_mb:.2f} MB/s")
        elif d['status'] == 'finished':
            self.log.debug(f"  ✅ Download complete, processing...")
    
    def download_single(self, url: str, quality: str = 'best', idx=None, total=None):
        start = time.time()
        output_template = str(self.save_dir / '%(title)s.%(ext)s')
        
        self.log.download_start(url, idx, total)
        
        try:
            ydl_opts = self._get_ydl_opts(quality, output_template, self._progress_hook)
            
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=True)
                filename = ydl.prepare_filename(info)
                
                if os.path.exists(filename):
                    size_mb = os.path.getsize(filename) / 1024 / 1024
                else:
                    files = [f for f in self.save_dir.glob('*.mp4') if f.is_file()]
                    if files:
                        latest = max(files, key=lambda f: f.stat().st_mtime)
                        size_mb = latest.stat().st_size / 1024 / 1024
                        filename = str(latest)
                    else:
                        size_mb = 0
                
                duration = time.time() - start
                self.stats['success'] += 1
                self.stats['total_size_mb'] += size_mb
                self.stats['total_duration'] += duration
                
                self.log.download_finish(url, filename, size_mb, duration, idx, total)
                return True
                
        except Exception as e:
            self.stats['failed'] += 1
            self.log.download_error(url, str(e), idx, total)
            return False
    
    def download_bulk(self, urls: List[str], quality: str = 'best'):
        if not urls:
            self.log.warning("No URLs provided.")
            return
        
        self.stats['total'] = len(urls)
        self.log.info(f"\n📦 Bulk: {len(urls)} items, Quality: {quality.upper()}")
        
        for i, url in enumerate(urls, 1):
            self.log.info(f"\n{'─'*50}")
            self.log.info(f"📥 Item {i}/{len(urls)}")
            self.download_single(url.strip(), quality, idx=i, total=len(urls))
            if i < len(urls):
                time.sleep(2)
        
        self._print_summary()
    
    def download_profile_or_hashtag(self, target: str, quality: str = 'best', limit: Optional[int] = None):
        if target.startswith('@'):
            url = f"https://www.tiktok.com/{target}"
            label = "Profile"
        else:
            url = f"https://www.tiktok.com/tag/{target}"
            label = "Hashtag"
        
        self.log.info(f"\n📁 {label}: {url}")
        self.log.info(f"📁 Save location: {self.save_dir}")
        self.log.info(f"📏 Limit: {limit or 'All'}")
        
        try:
            output_template = str(self.save_dir / '%(title)s.%(ext)s')
            ydl_opts = self._get_ydl_opts(quality, output_template)
            ydl_opts['extract_flat'] = False
            ydl_opts['playlistend'] = limit if limit else None
            
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                self.log.info("🔍 Fetching list...")
                info = ydl.extract_info(url, download=False)
                
                if 'entries' in info:
                    entries = [e for e in info['entries'] if e]
                    total_videos = len(entries)
                    self.log.info(f"📊 Found {total_videos} videos")
                    
                    self.stats['total'] = total_videos
                    for i, entry in enumerate(entries, 1):
                        video_url = f"https://www.tiktok.com/@user/video/{entry['id']}"
                        self.log.info(f"\n{'─'*50}")
                        self.log.info(f"📥 Video {i}/{total_videos}")
                        self.download_single(video_url, quality, idx=i, total=total_videos)
                        if i < total_videos:
                            time.sleep(3)
                    
                    self._print_summary()
                    return True
                else:
                    self.log.info("📹 Single video detected")
                    self.stats['total'] = 1
                    return self.download_single(url, quality, idx=1, total=1)
                    
        except Exception as e:
            self.log.error(f"❌ Failed: {e}")
            return False
    
    def _print_summary(self):
        self.log.info("\n" + "="*50)
        self.log.info("📊 SUMMARY")
        self.log.info("="*50)
        self.log.info(f"📦 Total:   {self.stats['total']}")
        self.log.info(f"✅ Success: {self.stats['success']}")
        self.log.info(f"❌ Failed:  {self.stats['failed']}")
        self.log.info(f"💾 Size:    {self.stats['total_size_mb']:.2f} MB")
        self.log.info(f"⏱️  Time:    {self.stats['total_duration']:.1f}s")
        if self.stats['success'] > 0:
            self.log.info(f"📏 Avg:     {self.stats['total_size_mb']/self.stats['success']:.2f} MB")
        self.log.info(f"📁 Log:     {self.log.get_log_path()}")
        self.log.info(f"📁 Videos:  {self.save_dir}")
        self.log.info("="*50)
    
    def interactive(self):
        self.log.info("\n🐱 TIKTOK SUPREME - VIDEO DOWNLOADER")
        while True:
            print("\n" + "="*60)
            print("🐱 TIKTOK SUPREME VIDEO DOWNLOADER")
            print(f"📁 Videos save to: {self.save_dir}")
            print("="*60)
            print("\n📌 OPTIONS:")
            print("  1. Single video          - Download one video from a URL")
            print("  2. Multiple videos       - Download multiple videos from a list of URLs")
            print("  3. Profile (@username)   - Download ALL videos from a TikTok user's profile")
            print("  4. Hashtag               - Download ALL videos with a specific hashtag")
            print("  5. Exit                  - Close the program")
            print("\n" + "="*60)
            
            choice = input("\n👉 Enter your choice (1-5): ").strip()
            
            if choice == '5':
                self.log.info("👋 Goodbye, Butter. CAT is always ready.")
                print(f"\n📁 Log saved to: {self.log.get_log_path()}")
                print(f"📁 Videos saved in: {self.save_dir}")
                break
            
            print("\n📊 Select video quality:")
            print("  1. Best   - Maximum quality available")
            print("  2. High   - 1080p")
            print("  3. Medium - 720p")
            print("  4. Low    - 480p")
            
            q_choice = input("👉 Enter quality (1-4) [default: 1]: ").strip()
            q_map = {'1': 'best', '2': 'high', '3': 'medium', '4': 'low'}
            quality = q_map.get(q_choice, 'best')
            
            self.stats = {'total': 0, 'success': 0, 'failed': 0, 'total_size_mb': 0, 'total_duration': 0}
            
            if choice == '1':
                print("\n📌 Single video download")
                print("   Paste a TikTok video URL (e.g., https://www.tiktok.com/@user/video/123)")
                url = input("\n🔗 URL: ").strip()
                if url:
                    self.stats['total'] = 1
                    self.download_single(url, quality, idx=1, total=1)
                    self._print_summary()
                else:
                    self.log.warning("No URL provided.")
                    
            elif choice == '2':
                print("\n📌 Multiple videos download")
                print("   Paste multiple TikTok URLs (one per line)")
                print("   Press Enter twice when done")
                print("\n📋 Enter URLs:")
                lines = []
                while True:
                    line = input()
                    if not line:
                        break
                    lines.append(line)
                urls = [u for u in lines if u.strip()]
                if urls:
                    self.download_bulk(urls, quality)
                else:
                    self.log.warning("No valid URLs provided.")
                    
            elif choice == '3':
                print("\n📌 Profile download")
                print("   Downloads ALL videos from a TikTok user's profile")
                print("   Example: @cat, @kuych.tt, @tiktok")
                profile = input("\n👤 Enter profile username (with @, e.g., @cat): ").strip()
                if profile:
                    print("\n📏 Limit the number of videos to download")
                    print("   Press Enter to download ALL (could be hundreds)")
                    limit_input = input("👉 Enter limit (or press Enter for all): ").strip()
                    limit = int(limit_input) if limit_input.isdigit() else None
                    if limit:
                        print(f"   ⚠️  Will download the latest {limit} videos")
                    else:
                        print("   ⚠️  Will download ALL videos (may take a while)")
                    self.download_profile_or_hashtag(profile, quality, limit)
                else:
                    self.log.warning("No profile provided.")
                    
            elif choice == '4':
                print("\n📌 Hashtag download")
                print("   Downloads ALL videos tagged with a specific hashtag")
                print("   Example: fyp, funny, cat, tiktok")
                hashtag = input("\n#️⃣ Enter hashtag (without #, e.g., fyp): ").strip()
                if hashtag:
                    print("\n📏 Limit the number of videos to download")
                    print("   Press Enter to download ALL (could be hundreds)")
                    limit_input = input("👉 Enter limit (or press Enter for all): ").strip()
                    limit = int(limit_input) if limit_input.isdigit() else None
                    if limit:
                        print(f"   ⚠️  Will download the latest {limit} videos")
                    else:
                        print("   ⚠️  Will download ALL videos (may take a while)")
                    self.download_profile_or_hashtag(f"#{hashtag}", quality, limit)
                else:
                    self.log.warning("No hashtag provided.")
            else:
                self.log.warning("Invalid choice. Please select 1-5.")
                continue
            
            again = input("\n🔄 Download another batch? (y/n): ").strip().lower()
            if again != 'y':
                self.log.info("👋 Goodbye, Butter. CAT is always ready.")
                print(f"\n📁 Log saved to: {self.log.get_log_path()}")
                print(f"📁 Videos saved in: {self.save_dir}")
                break

def main():
    downloader = TikTokSupreme()
    downloader.interactive()

if __name__ == "__main__":
    main()