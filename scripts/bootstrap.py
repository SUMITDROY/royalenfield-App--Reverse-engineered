#!/usr/bin/env python3
"""Fetch and verify the pinned compile-only Xposed API dependency."""
from build_support import xposed_api
print(xposed_api(download=True))
