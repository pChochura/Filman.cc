with open("feature/details/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsViewModel.kt", "r") as f:
    lines = f.readlines()

for i in range(510, 530):
    if "launchHandled {" in lines[i]:
        lines[i] = lines[i].replace("launchHandled {", "launchHandled(onError = { updateState { state -> state.copy(isExtractingLinks = false) }; handleError(it) }) {")
        break

with open("feature/details/src/main/java/com/pointlessapps/filman/ui/details/MovieDetailsViewModel.kt", "w") as f:
    f.writelines(lines)
