package guides

import (
	"fmt"
	"os/exec"
)

func Install(guideType Type) bool {
	packageName := fmt.Sprintf("@%s/%s@latest", guideType.Organization, guideType.PackageName)
	fmt.Printf("Installing npm package %s\n", packageName)

	cmd := exec.Command(
		"npm",
		"install",
		packageName,
		"--registry=https://npm.pkg.github.com",
	)

	cmd.Dir = "./"
	output, err := cmd.CombinedOutput()
	if err != nil {
		fmt.Printf("npm install failed: %v\n%s", err, string(output))

		return false
	}

	fmt.Println("npm install completed.")
	return true
}
