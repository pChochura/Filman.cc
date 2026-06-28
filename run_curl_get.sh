#!/bin/bash
COOKIE="PHPSESSID=9am2t5lekt01algj8mp3rb0i72; BKD_REMEMBER=BmNJLd1EWOTxh9oMqSauHI6Ag7UFt3r8nz4skjifDbPRvV5Cy2KGwXcplY; user_id=1811721"
UA="Mozilla/5.0 (Linux; Android 16; sdk_google_atv64_arm64 Build/BT2A.260319.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/143.0.7499.24 Mobile Safari/537.36"
curl -s -D - -H "User-Agent: $UA" -H "Cookie: $COOKIE" "https://filman.cc/film/robin-hood-koniec-legendy-the-death-of-robin-hood-2026" -o /dev/null
