#!/usr/bin/env python3
"""
Vista Project - Automated YouTube Base APK Downloader
Reliably downloads official nodpi base YouTube APK directly from APKMirror
"""

import sys
import os
import re
import urllib.request
import http.cookiejar
from bs4 import BeautifulSoup

HEADERS = {
    'user-agent': 'APKUpdater-v0',
    'authorization': 'Basic YXBpLWFwa3VwZGF0ZXI6cm01cmNmcnVVakt5MDRzTXB5TVBKWFc4'
}
BASE = 'https://www.apkmirror.com'

class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Don't forward internal APKMirror auth to CDN storage
        return urllib.request.Request(newurl, headers={'user-agent': 'Mozilla/5.0 (Android; Mobile)'})

cj = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj), NoRedirectHandler)

def fetch_html(url, referer=None):
    h = dict(HEADERS)
    if referer:
        h['Referer'] = referer
    req = urllib.request.Request(url, headers=h)
    with opener.open(req) as resp:
        return resp.read().decode('utf-8', errors='ignore')

def download_file(url, out_path, referer=None):
    h = dict(HEADERS)
    if referer:
        h['Referer'] = referer
    req = urllib.request.Request(url, headers=h)
    with opener.open(req) as resp:
        total = int(resp.headers.get('Content-Length', 0))
        downloaded = 0
        print(f"Downloading {total / (1024*1024):.1f} MB to {out_path}...")
        with open(out_path, 'wb') as f:
            while True:
                chunk = resp.read(1024 * 512)
                if not chunk:
                    break
                f.write(chunk)
                downloaded += len(chunk)
                if total > 0:
                    pct = int(downloaded * 100 / total)
                    print(f"\rProgress: {pct}% ({downloaded // (1024*1024)}MB / {total // (1024*1024)}MB)", end='', flush=True)
        print("\nDownload completed successfully!")

def main():
    version = sys.argv[1] if len(sys.argv) > 1 else '21.39.522'
    out_path = sys.argv[2] if len(sys.argv) > 2 else 'youtube.apk'

    slug = version.replace('.', '-')
    release_url = f"{BASE}/apk/google-inc/youtube/youtube-{slug}-release"

    print(f"Fetching release page: {release_url}")
    html = fetch_html(release_url)
    soup = BeautifulSoup(html, 'html.parser')

    # Select APK row with nodpi / universal
    table = soup.find('div', class_='variants-table')
    if not table:
        print("Error: variants-table not found on APKMirror page.")
        sys.exit(1)

    variant_url = None
    for r in table.find_all('div', class_='table-row'):
        badge = r.find('span', class_='apkm-badge')
        if badge and badge.text.strip() == 'APK':
            text = r.text.lower()
            if 'nodpi' in text or 'universal' in text:
                for a in r.find_all('a'):
                    href = a.get('href', '')
                    if href.endswith('-android-apk-download/'):
                        variant_url = BASE + href
                        break
            if variant_url:
                break

    if not variant_url:
        print("Error: Could not locate nodpi APK variant link.")
        sys.exit(1)

    print(f"Found APK variant URL: {variant_url}")

    # Fetch variant page
    v_html = fetch_html(variant_url)
    v_soup = BeautifulSoup(v_html, 'html.parser')
    btn = v_soup.find('a', class_='downloadButton')
    if not btn or not btn.get('href'):
        print("Error: Could not locate downloadButton on variant page.")
        sys.exit(1)

    dl_page_url = BASE + btn['href']
    print(f"Found download page: {dl_page_url}")

    # Fetch download page
    d_html = fetch_html(dl_page_url, referer=variant_url)
    d_soup = BeautifulSoup(d_html, 'html.parser')
    direct_a = d_soup.select_one('.card-with-tabs a[href]')
    if not direct_a or not direct_a.get('href'):
        print("Error: Could not locate direct download link.")
        sys.exit(1)

    direct_url = BASE + direct_a['href']
    print(f"Direct stream URL: {direct_url}")

    # Download direct file
    download_file(direct_url, out_path, referer=dl_page_url)

if __name__ == '__main__':
    main()
