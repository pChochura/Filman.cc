with open("app/src/main/java/com/pointlessapps/filman/FilmanApplication.kt", "r") as f:
    lines = f.readlines()

new_lines = []
in_func = False
for line in lines:
    if line.startswith("fun getUnsafeOkHttpClient"):
        in_func = True
    if in_func:
        if line.startswith("}"):
            in_func = False
        continue
    new_lines.append(line)

new_lines.insert(20, "import com.pointlessapps.filman.data.scraper.getUnsafeOkHttpClient\n")

with open("app/src/main/java/com/pointlessapps/filman/FilmanApplication.kt", "w") as f:
    f.writelines(new_lines)
