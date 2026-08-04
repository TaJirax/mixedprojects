#!/usr/bin/env python3
"""
YouTube Supreme Downloader - Single File (with Cookies Support)
Downloads: Single videos, Shorts, Playlists, Audio-only
All qualities: Best, High (1080p), Medium (720p), Low (480p)
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
        log_file = self.log_dir / f"youtube_downloader_{timestamp}.log"
        
        self.logger = logging.getLogger('YouTubeDownloader')
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
        self.logger.info(f"🎬 YOUTUBE SUPREME DOWNLOADER (with Cookies)")
        self.logger.info(f"📅 {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        self.logger.info(f"📁 Log: {log_file}")
        self.logger.info(f"📁 Save location: {download_dir('youtube')}")
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
class YouTubeSupreme:
    def __init__(self):
        self.save_dir = download_dir("youtube")
        self.log = LogManager()
        self.stats = {'total': 0, 'success': 0, 'failed': 0, 'total_size_mb': 0, 'total_duration': 0}
        self.log.info(f"📁 Files will be saved to: {self.save_dir}")
        
        # Try to detect browser cookies
        self.cookie_file = self._detect_cookies()
        if self.cookie_file:
            self.log.info(f"🍪 Using cookies from: {self.cookie_file}")
        else:
            self.log.warning("⚠️ No cookies found. You may encounter 'bot' errors.")
            self.log.warning("📖 See: https://github.com/yt-dlp/yt-dlp#how-do-i-pass-cookies-to-yt-dlp")
    
    def _detect_cookies(self) -> Optional[str]:
        """Detect cookies from common browsers."""
        import platform
        system = platform.system()
        
        cookie_paths = []
        
        if system == 'Windows':
            cookie_paths = [
                os.path.expanduser('~/AppData/Local/Google/Chrome/User Data/Default/Cookies'),
                os.path.expanduser('~/AppData/Local/Microsoft/Edge/User Data/Default/Cookies'),
                os.path.expanduser('~/AppData/Roaming/Mozilla/Firefox/Profiles/*/cookies.sqlite'),
            ]
        elif system == 'Darwin':  # macOS
            cookie_paths = [
                os.path.expanduser('~/Library/Application Support/Google/Chrome/Default/Cookies'),
                os.path.expanduser('~/Library/Application Support/Microsoft Edge/Default/Cookies'),
                os.path.expanduser('~/Library/Application Support/Firefox/Profiles/*/cookies.sqlite'),
            ]
        else:  # Linux
            cookie_paths = [
                os.path.expanduser('~/.config/google-chrome/Default/Cookies'),
                os.path.expanduser('~/.config/microsoft-edge/Default/Cookies'),
                os.path.expanduser('~/.mozilla/firefox/*/cookies.sqlite'),
            ]
        
        for path in cookie_paths:
            if '*' in path:
                import glob
                matches = glob.glob(path)
                if matches:
                    return matches[0]
            elif os.path.exists(path):
                return path
        
        return None
    
    def _get_ydl_opts(self, quality: str, output_template: str, is_audio: bool = False, progress_hook=None):
        """Generate yt-dlp options for YouTube with cookies."""
        
        # Base options
        ydl_opts = {
            'outtmpl': output_template,
            'quiet': True,
            'no_warnings': True,
            'ignoreerrors': True,
            'extract_flat': False,
            'sleep_interval': 1,
            'max_sleep_interval': 3,
            'socket_timeout': 30,
            'retries': 10,
            'headers': {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            },
            'progress_hooks': [progress_hook] if progress_hook else [],
        }
        
        # Add cookies if available
        if self.cookie_file:
            ydl_opts['cookiefile'] = self.cookie_file
        
        if is_audio:
            # Audio only - MP3 extraction
            ydl_opts.update({
                'format': 'bestaudio/best',
                'postprocessors': [{
                    'key': 'FFmpegExtractAudio',
                    'preferredcodec': 'mp3',
                    'preferredquality': '192',
                }],
            })
        else:
            # Video - quality selection
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
            
            ydl_opts.update({
                'format': format_spec,
                'merge_output_format': 'mp4',
                'postprocessors': [],
            })
        
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
    
    def download_single(self, url: str, quality: str = 'best', is_audio: bool = False, idx=None, total=None):
        """Download a single video or audio."""
        start = time.time()
        
        # Check if it's a Short
        is_short = 'shorts/' in url.lower()
        if is_short and not is_audio:
            self.log.info(f"  📱 YouTube Short detected")
        
        output_template = str(self.save_dir / '%(title)s.%(ext)s')
        
        self.log.download_start(url, idx, total)
        if is_audio:
            self.log.info(f"  🎵 Audio mode: downloading MP3")
        if is_short:
            self.log.info(f"  📱 Short video mode")
        
        try:
            ydl_opts = self._get_ydl_opts(quality, output_template, is_audio, self._progress_hook)
            
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=True)
                
                if is_audio:
                    base = ydl.prepare_filename(info)
                    filename = os.path.splitext(base)[0] + '.mp3'
                else:
                    filename = ydl.prepare_filename(info)
                
                # Get file size
                if os.path.exists(filename):
                    size_mb = os.path.getsize(filename) / 1024 / 1024
                else:
                    # Search for the file
                    ext = 'mp3' if is_audio else 'mp4'
                    files = [f for f in self.save_dir.glob(f'*.{ext}') if f.is_file()]
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
            error_msg = str(e)
            self.stats['failed'] += 1
            self.log.download_error(url, error_msg, idx, total)
            
            # Special error handling for cookie issues
            if "Sign in to confirm you’re not a bot" in error_msg:
                self.log.error("\n" + "="*60)
                self.log.error("🔐 YOUTUBE BOT DETECTION FIX")
                self.log.error("="*60)
                self.log.error("YouTube is blocking your request. Here's how to fix it:")
                self.log.error("")
                self.log.error("OPTION 1: Use your browser's cookies (automatic)")
                self.log.error("  1. Make sure you're logged into YouTube in your browser")
                self.log.error("  2. Run this script again - it should auto-detect your cookies")
                self.log.error("")
                self.log.error("OPTION 2: Export cookies manually")
                self.log.error("  1. Install browser extension: 'Get cookies.txt' or similar")
                self.log.error("  2. Log into YouTube and export cookies as 'cookies.txt'")
                self.log.error("  3. Place 'cookies.txt' in the same folder as this script")
                self.log.error("  4. Run the script again")
                self.log.error("")
                self.log.error("OPTION 3: Use --cookies-from-browser (command line)")
                self.log.error("  python youtube_downloader.py --cookies-from-browser chrome")
                self.log.error("")
                self.log.error("📖 Full guide: https://github.com/yt-dlp/yt-dlp#how-do-i-pass-cookies-to-yt-dlp")
                self.log.error("="*60)
            
            return False
    
    def download_playlist(self, url: str, quality: str = 'best', is_audio: bool = False, limit: Optional[int] = None):
        """Download an entire playlist."""
        self.log.info(f"\n📁 PLAYLIST DETECTED")
        self.log.info(f"📁 Save location: {self.save_dir}")
        self.log.info(f"📏 Limit: {limit or 'All videos'}")
        if is_audio:
            self.log.info(f"🎵 Audio mode: downloading all as MP3")
        
        try:
            output_template = str(self.save_dir / '%(title)s.%(ext)s')
            ydl_opts = self._get_ydl_opts(quality, output_template, is_audio)
            ydl_opts['extract_flat'] = False
            ydl_opts['playlistend'] = limit if limit else None
            
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                self.log.info("🔍 Fetching playlist...")
                info = ydl.extract_info(url, download=False)
                
                if 'entries' in info:
                    entries = [e for e in info['entries'] if e]
                    total_videos = len(entries)
                    self.log.info(f"📊 Found {total_videos} videos in playlist")
                    
                    self.stats['total'] = total_videos
                    for i, entry in enumerate(entries, 1):
                        video_url = f"https://www.youtube.com/watch?v={entry['id']}"
                        self.log.info(f"\n{'─'*50}")
                        self.log.info(f"📥 Video {i}/{total_videos}")
                        self.download_single(video_url, quality, is_audio, idx=i, total=total_videos)
                        if i < total_videos:
                            time.sleep(2)
                    
                    self._print_summary()
                    return True
                else:
                    self.log.info("📹 Single video detected (not a playlist)")
                    self.stats['total'] = 1
                    return self.download_single(url, quality, is_audio, idx=1, total=1)
                    
        except Exception as e:
            self.log.error(f"❌ Failed to process playlist: {e}")
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
        self.log.info(f"📁 Files:   {self.save_dir}")
        self.log.info("="*50)
    
    def interactive(self):
        self.log.info("\n🎬 YOUTUBE SUPREME DOWNLOADER")
        while True:
            print("\n" + "="*60)
            print("🎬 YOUTUBE SUPREME DOWNLOADER")
            print(f"📁 Files save to: {self.save_dir}")
            if self.cookie_file:
                print(f"🍪 Cookies: Loaded from browser")
            else:
                print(f"⚠️  No cookies found - may trigger bot detection")
            print("="*60)
            print("\n📌 OPTIONS:")
            print("  1. Single video          - Download one video from a URL")
            print("  2. Single audio          - Download audio only (MP3) from a URL")
            print("  3. Playlist              - Download ALL videos from a playlist")
            print("  4. Playlist (audio)      - Download ALL audio (MP3) from a playlist")
            print("  5. Multiple videos       - Download multiple videos from a list of URLs")
            print("  6. YouTube Short         - Download a YouTube Short video")
            print("  7. Exit                  - Close the program")
            print("\n" + "="*60)
            
            choice = input("\n👉 Enter your choice (1-7): ").strip()
            
            if choice == '7':
                self.log.info("👋 Goodbye, Butter. CAT is always ready.")
                print(f"\n📁 Log saved to: {self.log.get_log_path()}")
                print(f"📁 Files saved in: {self.save_dir}")
                break
            
            # Determine audio mode
            is_audio = False
            if choice in ['2', '4']:
                is_audio = True
            
            print("\n📊 Select quality:")
            print("  1. Best   - Maximum quality available")
            print("  2. High   - 1080p")
            print("  3. Medium - 720p")
            print("  4. Low    - 480p")
            if is_audio:
                print("  ℹ️  For audio, quality affects bitrate (192kbps MP3)")
            
            q_choice = input("👉 Enter quality (1-4) [default: 1]: ").strip()
            q_map = {'1': 'best', '2': 'high', '3': 'medium', '4': 'low'}
            quality = q_map.get(q_choice, 'best')
            
            self.stats = {'total': 0, 'success': 0, 'failed': 0, 'total_size_mb': 0, 'total_duration': 0}
            
            if choice == '1':  # Single video
                print("\n📌 Single video download")
                print("   Paste a YouTube video URL")
                print("   Examples: https://www.youtube.com/watch?v=VIDEO_ID")
                print("             https://youtu.be/VIDEO_ID")
                url = input("\n🔗 URL: ").strip()
                if url:
                    self.stats['total'] = 1
                    self.download_single(url, quality, is_audio=False, idx=1, total=1)
                    self._print_summary()
                else:
                    self.log.warning("No URL provided.")
            
            elif choice == '2':  # Single audio
                print("\n📌 Single audio download (MP3)")
                print("   Paste a YouTube video URL")
                print("   The audio will be extracted as MP3")
                url = input("\n🔗 URL: ").strip()
                if url:
                    self.stats['total'] = 1
                    self.download_single(url, quality, is_audio=True, idx=1, total=1)
                    self._print_summary()
                else:
                    self.log.warning("No URL provided.")
            
            elif choice == '3':  # Playlist video
                print("\n📌 Playlist download")
                print("   Downloads ALL videos from a YouTube playlist")
                print("   Example: https://www.youtube.com/playlist?list=PLAYLIST_ID")
                url = input("\n🔗 Playlist URL: ").strip()
                if url:
                    print("\n📏 Limit the number of videos to download")
                    print("   Press Enter to download ALL (could be hundreds)")
                    limit_input = input("👉 Enter limit (or press Enter for all): ").strip()
                    limit = int(limit_input) if limit_input.isdigit() else None
                    if limit:
                        print(f"   ⚠️  Will download the first {limit} videos")
                    else:
                        print("   ⚠️  Will download ALL videos (may take a while)")
                    self.download_playlist(url, quality, is_audio=False, limit=limit)
                else:
                    self.log.warning("No URL provided.")
            
            elif choice == '4':  # Playlist audio
                print("\n📌 Playlist audio download (MP3)")
                print("   Downloads ALL audio as MP3 from a YouTube playlist")
                print("   Example: https://www.youtube.com/playlist?list=PLAYLIST_ID")
                url = input("\n🔗 Playlist URL: ").strip()
                if url:
                    print("\n📏 Limit the number of videos to download")
                    print("   Press Enter to download ALL (could be hundreds)")
                    limit_input = input("👉 Enter limit (or press Enter for all): ").strip()
                    limit = int(limit_input) if limit_input.isdigit() else None
                    if limit:
                        print(f"   ⚠️  Will download the first {limit} audios")
                    else:
                        print("   ⚠️  Will download ALL audios (may take a while)")
                    self.download_playlist(url, quality, is_audio=True, limit=limit)
                else:
                    self.log.warning("No URL provided.")
            
            elif choice == '5':  # Multiple videos
                print("\n📌 Multiple videos download")
                print("   Paste multiple YouTube URLs (one per line)")
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
                    self.stats['total'] = len(urls)
                    self.log.info(f"\n📦 Bulk: {len(urls)} items, Quality: {quality.upper()}")
                    for i, url in enumerate(urls, 1):
                        self.log.info(f"\n{'─'*50}")
                        self.log.info(f"📥 Item {i}/{len(urls)}")
                        self.download_single(url.strip(), quality, is_audio=False, idx=i, total=len(urls))
                        if i < len(urls):
                            time.sleep(2)
                    self._print_summary()
                else:
                    self.log.warning("No valid URLs provided.")
            
            elif choice == '6':  # YouTube Short
                print("\n📌 YouTube Short download")
                print("   Downloads Short videos (vertical format)")
                print("   Example: https://www.youtube.com/shorts/SHORT_ID")
                url = input("\n🔗 Short URL: ").strip()
                if url:
                    self.stats['total'] = 1
                    self.download_single(url, quality, is_audio=False, idx=1, total=1)
                    self._print_summary()
                else:
                    self.log.warning("No URL provided.")
            
            else:
                self.log.warning("Invalid choice. Please select 1-7.")
                continue
            
            again = input("\n🔄 Download another? (y/n): ").strip().lower()
            if again != 'y':
                self.log.info("👋 Goodbye, Butter. CAT is always ready.")
                print(f"\n📁 Log saved to: {self.log.get_log_path()}")
                print(f"📁 Files saved in: {self.save_dir}")
                break

def main():
    downloader = YouTubeSupreme()
    downloader.interactive()

if __name__ == "__main__":
    main()