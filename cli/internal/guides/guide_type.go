package guides

type Type struct {
	Name string
}

var TypeByName = map[string]Type{
	TypeSocialMediaApp.Name: TypeSocialMediaApp,
}

var SupportedGuideTypes = []string{TypeSocialMediaApp.Name}

var (
	TypeSocialMediaApp = Type{Name: "hands-on-social"}
)

func TypeFromString(name string) (Type, bool) {
	s, ok := TypeByName[name]
	return s, ok
}
