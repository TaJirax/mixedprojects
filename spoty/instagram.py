#!/usr/bin/env python3
"""
Instagram Supreme Downloader - Single File
Downloads: Single video, Images, Profile posts, All media from a page
Saves files in the same folder as this script.
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
import json

# --- AUTO-INSTALL ---
def ensure_module(module_name: str, pip_name: str = None):
    if pip_name is None:
        pip_name = module_name
    spec = importlib.util.find_spec(module_name)
    if spec is None:
        print(f"⚡️ {module_name} not found. Installing...")
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

from blueknight_paths import COOKIE_FAILURE, cookie_candidates, download_dir, ytdlp_cookie_opts

# --- LOGGER ---
class LogManager:
    def __init__(self, log_dir: str = "logs"):
        self.log_dir = SCRIPT_DIR / log_dir
        self.log_dir.mkdir(parents=True, exist_ok=True)
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        log_file = self.log_dir / f"instagram_downloader_{timestamp}.log"
        
        self.logger = logging.getLogger('InstagramDownloader')
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
        self.logger.info(f"📸 INSTAGRAM SUPREME DOWNLOADER")
        self.logger.info(f"📅 {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        self.logger.info(f"📁 Log: {log_file}")
        self.logger.info(f"📁 Save location: {download_dir('instagram')}")
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
class InstagramSupreme:
    def __init__(self):
        self.save_dir = download_dir("instagram")
        self.log = LogManager()
        self.stats = {'total': 0, 'success': 0, 'failed': 0, 'total_size_mb': 0, 'total_duration': 0}
        self.log.info(f"📁 Files will be saved to: {self.save_dir}")

        # A browser's `Cookies` file is a SQLite DB, not a Netscape cookies.txt, so
        # it has to go to yt-dlp as cookiesfrombrowser. See blueknight_paths.
        self.cookie_list = cookie_candidates()
        self.cookies = self.cookie_list[0] if self.cookie_list else None
        if self.cookies:
            kind, value = self.cookies
            self.log.info(f"🍪 Using cookies from {'file' if kind == 'file' else 'browser'}: {value}")
        else:
            self.log.warning("⚠️ No cookies found. Instagram may limit downloads.")
            self.log.warning("📖 Login to Instagram in your browser, or drop a cookies.txt next to this script.")

    def _get_ydl_opts(self, output_template: str, is_image: bool = False, progress_hook=None):
        """Generate yt-dlp options for Instagram."""
        
        ydl_opts = {
            'outtmpl': output_template,
            'quiet': True,
            'no_warnings': True,
            'ignoreerrors': True,
            'extract_flat': False,
            'sleep_interval': 2,
            'max_sleep_interval': 5,
            'socket_timeout': 30,
            'retries': 10,
            'headers': {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            },
            'progress_hooks': [progress_hook] if progress_hook else [],
        }
        
        ydl_opts.update(ytdlp_cookie_opts(self.cookies))

        # Instagram-specific options
        if is_image:
            # For images, get the best quality
            ydl_opts['format'] = 'best'
        else:
            # For videos, get best quality
            ydl_opts['format'] = 'best[ext=mp4]/best'
        
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
    
    def download_single(self, url: str, idx=None, total=None):
        """Download a single Instagram post (video or image)."""
        start = time.time()
        
        # Detect if it's a video or image
        is_video = '/reel/' in url.lower() or '/tv/' in url.lower()
        is_image = '/p/' in url.lower()
        
        media_type = "Video" if is_video else "Image" if is_image else "Post"
        self.log.info(f"  📸 {media_type} detected")
        
        # Create filename from post ID
        post_id = url.split('/')[-2] if url.endswith('/') else url.split('/')[-1]
        output_template = str(self.save_dir / f'instagram_{post_id}_%(title)s.%(ext)s')
        
        self.log.download_start(url, idx, total)
        
        try:
            ydl_opts = self._get_ydl_opts(output_template, is_image=is_image, progress_hook=self._progress_hook)
            
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=True)
                filename = ydl.prepare_filename(info)
                
                # Get file size
                if os.path.exists(filename):
                    size_mb = os.path.getsize(filename) / 1024 / 1024
                else:
                    # Search for the file
                    files = [f for f in self.save_dir.glob(f'instagram_{post_id}*') if f.is_file()]
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
            # A cookie source that exists is not one that reads: a running Chromium
            # browser locks its DB. Step to the next source and take one more run.
            if COOKIE_FAILURE.search(str(e)) and self.cookies in self.cookie_list:
                nxt = self.cookie_list.index(self.cookies) + 1
                if nxt < len(self.cookie_list):
                    self.log.warning(f"⚠️ Cookies from {self.cookies[1]} unreadable, "
                                     f"trying {self.cookie_list[nxt][1]}")
                    self.cookies = self.cookie_list[nxt]
                    return self.download_single(url, idx, total)
            self.stats['failed'] += 1
            self.log.download_error(url, str(e), idx, total)
            return False
    
    def download_profile(self, username: str, limit: Optional[int] = None, media_type: str = 'all'):
        """Download all posts from a profile."""
        url = f"https://www.instagram.com/{username}/"
        
        self.log.info(f"\n📁 PROFILE: {username}")
        self.log.info(f"📁 Save location: {self.save_dir}")
        self.log.info(f"📏 Limit: {limit or 'All posts'}")
        self.log.info(f"📸 Media type: {media_type.upper()}")
        
        try:
            output_template = str(self.save_dir / f'{username}_%(title)s.%(ext)s')
            ydl_opts = self._get_ydl_opts(output_template)
            ydl_opts['extract_flat'] = False
            ydl_opts['playlistend'] = limit if limit else None
            
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                self.log.info("🔍 Fetching profile posts...")
                info = ydl.extract_info(url, download=False)
                
                if 'entries' in info:
                    entries = [e for e in info['entries'] if e]
                    self.log.info(f"📊 Found {len(entries)} posts")
                    
                    # Filter by media type if specified
                    if media_type != 'all':
                        filtered = []
                        for entry in entries:
                            if 'url' in entry:
                                url_lower = entry['url'].lower()
                                if media_type == 'video' and ('/reel/' in url_lower or '/tv/' in url_lower):
                                    filtered.append(entry)
                                elif media_type == 'image' and '/p/' in url_lower:
                                    filtered.append(entry)
                        entries = filtered
                        self.log.info(f"📊 Filtered to {len(entries)} {media_type}s")
                    
                    self.stats['total'] = len(entries)
                    for i, entry in enumerate(entries, 1):
                        post_url = entry.get('url', '')
                        if not post_url:
                            continue
                        
                        self.log.info(f"\n{'─'*50}")
                        self.log.info(f"📥 Post {i}/{len(entries)}")
                        self.download_single(post_url, idx=i, total=len(entries))
                        
                        if i < len(entries):
                            time.sleep(2)
                    
                    self._print_summary()
                    return True
                else:
                    self.log.info("📹 No posts found or private profile")
                    return False
                    
        except Exception as e:
            self.log.error(f"❌ Failed to process profile: {e}")
            return False
    
    def download_all_media(self, url: str, limit: Optional[int] = None):
        """Download all media from a specific page/post."""
        self.log.info(f"\n📁 PAGE MEDIA DOWNLOAD")
        self.log.info(f"📁 URL: {url}")
        self.log.info(f"📏 Limit: {limit or 'All media'}")
        
        try:
            output_template = str(self.save_dir / '%(title)s.%(ext)s')
            ydl_opts = self._get_ydl_opts(output_template)
            ydl_opts['extract_flat'] = False
            ydl_opts['playlistend'] = limit if limit else None
            
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                self.log.info("🔍 Fetching media...")
                info = ydl.extract_info(url, download=False)
                
                if 'entries' in info:
                    entries = [e for e in info['entries'] if e]
                    self.log.info(f"📊 Found {len(entries)} media items")
                    
                    self.stats['total'] = len(entries)
                    for i, entry in enumerate(entries, 1):
                        media_url = entry.get('url', '')
                        if not media_url:
                            continue
                        
                        self.log.info(f"\n{'─'*50}")
                        self.log.info(f"📥 Media {i}/{len(entries)}")
                        self.download_single(media_url, idx=i, total=len(entries))
                        
                        if i < len(entries):
                            time.sleep(2)
                    
                    self._print_summary()
                    return True
                else:
                    self.log.info("📹 No media found")
                    return False
                    
        except Exception as e:
            self.log.error(f"❌ Failed to process page: {e}")
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
        self.log.info("\n📸 INSTAGRAM SUPREME DOWNLOADER")
        while True:
            print("\n" + "="*60)
            print("📸 INSTAGRAM SUPREME DOWNLOADER")
            print(f"📁 Files save to: {self.save_dir}")
            if self.cookies:
                print(f"🍪 Cookies: {self.cookies[0]} ({self.cookies[1]})")
            else:
                print(f"⚠️  No cookies found - login required for some content")
            print("="*60)
            print("\n📌 OPTIONS:")
            print("  1. Single video          - Download one Instagram Reel or TV video")
            print("  2. Single image          - Download one Instagram image post")
            print("  3. Profile download      - Download ALL posts from a profile")
            print("  4. Profile (images only) - Download only images from a profile")
            print("  5. Profile (videos only) - Download only videos from a profile")
            print("  6. All media from page   - Download all media from any Instagram page")
            print("  7. Exit                  - Close the program")
            print("\n" + "="*60)
            
            choice = input("\n👉 Enter your choice (1-7): ").strip()
            
            if choice == '7':
                self.log.info("👋 Goodbye, Butter. CAT is always ready.")
                print(f"\n📁 Log saved to: {self.log.get_log_path()}")
                print(f"📁 Files saved in: {self.save_dir}")
                break
            
            self.stats = {'total': 0, 'success': 0, 'failed': 0, 'total_size_mb': 0, 'total_duration': 0}
            
            if choice == '1':  # Single video
                print("\n📌 Single video download")
                print("   Paste an Instagram Reel or TV URL")
                print("   Examples: https://www.instagram.com/reel/VIDEO_ID/")
                print("             https://www.instagram.com/tv/VIDEO_ID/")
                url = input("\n🔗 URL: ").strip()
                if url:
                    self.stats['total'] = 1
                    self.download_single(url, idx=1, total=1)
                    self._print_summary()
                else:
                    self.log.warning("No URL provided.")
            
            elif choice == '2':  # Single image
                print("\n📌 Single image download")
                print("   Paste an Instagram image post URL")
                print("   Example: https://www.instagram.com/p/IMAGE_ID/")
                url = input("\n🔗 URL: ").strip()
                if url:
                    self.stats['total'] = 1
                    self.download_single(url, idx=1, total=1)
                    self._print_summary()
                else:
                    self.log.warning("No URL provided.")
            
            elif choice == '3':  # Profile - all
                print("\n📌 Profile download - ALL posts")
                print("   Downloads ALL posts (images + videos) from a profile")
                print("   Example: @instagram, @natgeo")
                username = input("\n👤 Enter username (without @): ").strip()
                if username:
                    print("\n📏 Limit the number of posts to download")
                    print("   Press Enter to download ALL (could be hundreds)")
                    limit_input = input("👉 Enter limit (or press Enter for all): ").strip()
                    limit = int(limit_input) if limit_input.isdigit() else None
                    if limit:
                        print(f"   ⚠️  Will download the first {limit} posts")
                    else:
                        print("   ⚠️  Will download ALL posts (may take a while)")
                    self.download_profile(username, limit, media_type='all')
                else:
                    self.log.warning("No username provided.")
            
            elif choice == '4':  # Profile - images only
                print("\n📌 Profile download - IMAGES ONLY")
                print("   Downloads ONLY images from a profile")
                print("   Example: @instagram, @natgeo")
                username = input("\n👤 Enter username (without @): ").strip()
                if username:
                    print("\n📏 Limit the number of images to download")
                    print("   Press Enter to download ALL (could be hundreds)")
                    limit_input = input("👉 Enter limit (or press Enter for all): ").strip()
                    limit = int(limit_input) if limit_input.isdigit() else None
                    if limit:
                        print(f"   ⚠️  Will download the first {limit} images")
                    else:
                        print("   ⚠️  Will download ALL images (may take a while)")
                    self.download_profile(username, limit, media_type='image')
                else:
                    self.log.warning("No username provided.")
            
            elif choice == '5':  # Profile - videos only
                print("\n📌 Profile download - VIDEOS ONLY")
                print("   Downloads ONLY videos (Reels/TV) from a profile")
                print("   Example: @instagram, @natgeo")
                username = input("\n👤 Enter username (without @): ").strip()
                if username:
                    print("\n📏 Limit the number of videos to download")
                    print("   Press Enter to download ALL (could be hundreds)")
                    limit_input = input("👉 Enter limit (or press Enter for all): ").strip()
                    limit = int(limit_input) if limit_input.isdigit() else None
                    if limit:
                        print(f"   ⚠️  Will download the first {limit} videos")
                    else:
                        print("   ⚠️  Will download ALL videos (may take a while)")
                    self.download_profile(username, limit, media_type='video')
                else:
                    self.log.warning("No username provided.")
            
            elif choice == '6':  # All media from page
                print("\n📌 All media from page")
                print("   Download ALL media from any Instagram page")
                print("   Examples: https://www.instagram.com/p/POST_ID/")
                print("             https://www.instagram.com/reel/REEL_ID/")
                print("             https://www.instagram.com/explore/tags/HASHTAG/")
                url = input("\n🔗 Page URL: ").strip()
                if url:
                    print("\n📏 Limit the number of media items to download")
                    print("   Press Enter to download ALL (could be hundreds)")
                    limit_input = input("👉 Enter limit (or press Enter for all): ").strip()
                    limit = int(limit_input) if limit_input.isdigit() else None
                    if limit:
                        print(f"   ⚠️  Will download the first {limit} items")
                    else:
                        print("   ⚠️  Will download ALL items (may take a while)")
                    self.download_all_media(url, limit)
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
    downloader = InstagramSupreme()
    downloader.interactive()

if __name__ == "__main__":
    main()