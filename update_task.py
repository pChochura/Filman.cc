import sys

file = "/Users/pipistrelus/.gemini/antigravity/brain/014ef456-bf41-4d7f-bdf6-0af039642132/task.md"
with open(file, "r") as f:
    content = f.read()

content = content.replace("- [ ] Item 8 (Point 23): Feature Modularisation", "- [x] Item 8 (Point 23): Feature Modularisation (Phase 1)")

with open(file, "w") as f:
    f.write(content)
