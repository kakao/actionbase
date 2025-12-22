package guides

type Type struct {
	name         string
	Organization string
	PackageName  string
}

var TypeByName = map[string]Type{
	TypeSocialMediaApp.name: TypeSocialMediaApp,
}

var SupportedGuideTypes = []string{TypeSocialMediaApp.name}

var (
	TypeSocialMediaApp = Type{name: "hands-on-social", Organization: "kakao", PackageName: "actionbase-hands-on-social"}
)

func TypeFromString(name string) (Type, bool) {
	s, ok := TypeByName[name]
	return s, ok
}
