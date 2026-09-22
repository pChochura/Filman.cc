import sys
import re

# 1. MovieDetailsStateExt.kt
file1 = "app/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsStateExt.kt"
with open(file1, "r") as f:
    content1 = f.read()
content1 = content1.replace("baseItem.seasons.flatMapIndexed", "baseItem.seasons?.flatMapIndexed")
content1 = content1.replace("            } ?: emptyList()", "            }") # In case it was already added? Wait, I didn't add it yet.
content1 = content1.replace("                }
            }", "                }\n            } ?: emptyList()")
with open(file1, "w") as f:
    f.write(content1)

# 2. HomeViewModel.kt
file2 = "app/src/main/java/com/pointlessapps/filman/ui/home/HomeViewModel.kt"
with open(file2, "r") as f:
    content2 = f.read()
content2 = content2.replace("p.parentUrl.substringAfter", "p.parentUrl!!.substringAfter")
content2 = content2.replace("results.errorMessage.let(TextValue::DynamicString)", "results.errorMessage?.let(TextValue::DynamicString)")
with open(file2, "w") as f:
    f.write(content2)

# 3. SearchViewModel.kt
file3 = "app/src/main/java/com/pointlessapps/filman/ui/search/SearchViewModel.kt"
with open(file3, "r") as f:
    content3 = f.read()
content3 = content3.replace("results.errorMessage.let(TextValue::DynamicString)", "results.errorMessage?.let(TextValue::DynamicString)")
with open(file3, "w") as f:
    f.write(content3)

# 4. ForKidsViewModel.kt
file4 = "app/src/main/java/com/pointlessapps/filman/ui/forkids/ForKidsViewModel.kt"
with open(file4, "r") as f:
    content4 = f.read()
content4 = content4.replace("results.errorMessage.let(TextValue::DynamicString)", "results.errorMessage?.let(TextValue::DynamicString)")
with open(file4, "w") as f:
    f.write(content4)
