import os
import re

files_to_fix = [
    "feature/login/src/main/java/com/pointlessapps/filman/ui/login/LoginScreen.kt",
    "feature/player/src/main/java/com/pointlessapps/filman/ui/player/WebViewPlayer.kt"
]

for file in files_to_fix:
    with open(file, "r") as f:
        content = f.read()
    
    imports_to_add = []
    if "bypassRecaptchaAndLogin" in content and "import com.pointlessapps.filman.ui.core.bypassRecaptchaAndLogin" not in content:
        imports_to_add.append("import com.pointlessapps.filman.ui.core.bypassRecaptchaAndLogin")
    if "pointerMovement" in content and "import com.pointlessapps.filman.ui.core.pointerMovement" not in content:
        imports_to_add.append("import com.pointlessapps.filman.ui.core.pointerMovement")
    if "performClickAtCoordinates" in content and "import com.pointlessapps.filman.ui.core.performClickAtCoordinates" not in content:
        imports_to_add.append("import com.pointlessapps.filman.ui.core.performClickAtCoordinates")
    if "WebViewClient(" in content or "WebViewClient." in content:
        if "import com.pointlessapps.filman.ui.core.WebViewClient" not in content:
            imports_to_add.append("import com.pointlessapps.filman.ui.core.WebViewClient")
    
    if "PLAYER_PAUSE_SCRIPT" in content and "import com.pointlessapps.filman.ui.core.PLAYER_PAUSE_SCRIPT" not in content:
        imports_to_add.append("import com.pointlessapps.filman.ui.core.PLAYER_PAUSE_SCRIPT")
    if "PLAYER_PLAY_SCRIPT" in content and "import com.pointlessapps.filman.ui.core.PLAYER_PLAY_SCRIPT" not in content:
        imports_to_add.append("import com.pointlessapps.filman.ui.core.PLAYER_PLAY_SCRIPT")
    if "getPlayerAspectRatioScript" in content and "import com.pointlessapps.filman.ui.core.getPlayerAspectRatioScript" not in content:
        imports_to_add.append("import com.pointlessapps.filman.ui.core.getPlayerAspectRatioScript")
    if "getPlayerPlaybackSpeedScript" in content and "import com.pointlessapps.filman.ui.core.getPlayerPlaybackSpeedScript" not in content:
        imports_to_add.append("import com.pointlessapps.filman.ui.core.getPlayerPlaybackSpeedScript")
    if "getPlayerSetSubtitleScript" in content and "import com.pointlessapps.filman.ui.core.getPlayerSetSubtitleScript" not in content:
        imports_to_add.append("import com.pointlessapps.filman.ui.core.getPlayerSetSubtitleScript")
    if "getPlayerUserAgent" in content and "import com.pointlessapps.filman.ui.core.getPlayerUserAgent" not in content:
        imports_to_add.append("import com.pointlessapps.filman.ui.core.getPlayerUserAgent")
    if "playerWebChromeClient" in content and "import com.pointlessapps.filman.ui.core.playerWebChromeClient" not in content:
        imports_to_add.append("import com.pointlessapps.filman.ui.core.playerWebChromeClient")
    if "playerWebViewClient" in content and "import com.pointlessapps.filman.ui.core.playerWebViewClient" not in content:
        imports_to_add.append("import com.pointlessapps.filman.ui.core.playerWebViewClient")

    if imports_to_add:
        # insert right after the package declaration
        pkg_match = re.search(r'^package .*$', content, re.MULTILINE)
        if pkg_match:
            insert_pos = pkg_match.end()
            new_content = content[:insert_pos] + "\n\n" + "\n".join(imports_to_add) + content[insert_pos:]
            with open(file, "w") as f:
                f.write(new_content)

