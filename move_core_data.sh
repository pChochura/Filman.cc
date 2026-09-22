#!/bin/bash
mkdir -p core/data/src/main/java/com/pointlessapps/filman/data

# Move everything except extractors
mv app/src/main/java/com/pointlessapps/filman/data/local core/data/src/main/java/com/pointlessapps/filman/data/
mv app/src/main/java/com/pointlessapps/filman/data/mapper core/data/src/main/java/com/pointlessapps/filman/data/
mv app/src/main/java/com/pointlessapps/filman/data/model core/data/src/main/java/com/pointlessapps/filman/data/
mv app/src/main/java/com/pointlessapps/filman/data/recommendation core/data/src/main/java/com/pointlessapps/filman/data/
mv app/src/main/java/com/pointlessapps/filman/data/DataRepository.kt core/data/src/main/java/com/pointlessapps/filman/data/

# Scraper directory has extractors. Let's move scraper but leave extractors out for core:player?
# No, extractors are used by VideoUrlResolver which is in scraper.
# The roadmap says: :core:data — models, scrapers, DataStore managers, VideoUrlResolver
# And :core:player — ExoPlayer wrapper, NanoHTTPD proxy, all extractors
# So we need to separate them.
mkdir -p core/data/src/main/java/com/pointlessapps/filman/data/scraper
mv app/src/main/java/com/pointlessapps/filman/data/scraper/* core/data/src/main/java/com/pointlessapps/filman/data/scraper/
# Move extractors back to app for now (to be moved to core:player later)
mkdir -p app/src/main/java/com/pointlessapps/filman/data/scraper
mv core/data/src/main/java/com/pointlessapps/filman/data/scraper/extractors app/src/main/java/com/pointlessapps/filman/data/scraper/
