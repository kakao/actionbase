# Contributing

Thank you for your interest in Actionbase. Any form of participation—using the project, asking questions, reporting issues, improving documentation, or contributing code—is appreciated.

## Getting started

New to open source? Look for issues labeled **[good first issue](https://github.com/kakao/actionbase/labels/good%20first%20issue)**.

## Development setup

### Prerequisites

- Java 17+
- IntelliJ IDEA (Community or Ultimate)

### Setup

```bash
git clone https://github.com/kakao/actionbase.git
cd actionbase
```

Open in IntelliJ: **File > Open** → select project root.

IntelliJ will auto-import Gradle. Wait for indexing to complete.

### Build

```bash
./gradlew build
```

Or in IntelliJ: **Gradle panel > actionbase > Tasks > build > build**

### Format

Run before committing:

```bash
./gradlew spotlessApply
```

This formats Kotlin/Java code according to project style.

### Run

```bash
./gradlew :server:bootRun
```

Server starts at `http://localhost:8080`.

### Test

```bash
# All tests
./gradlew test

# Specific module
./gradlew :core:test
./gradlew :engine:test
./gradlew :server:test
```

## PR workflow

Fork [kakao/actionbase](https://github.com/kakao/actionbase) on GitHub, then set up remotes:

```bash
git remote rename origin upstream
git remote add origin https://github.com/YOUR_USERNAME/actionbase.git
```

1. Create branch: `git checkout -b feature/your-feature`
2. Make changes
3. Format: `./gradlew spotlessApply`
4. Test: `./gradlew test`
5. Commit: `git commit -m "feat(scope): description"`
6. Push & create PR

## Translations

Translations are managed through Translation Memory (TM) files. Here's how to contribute:

1. **Find documents that need translation.** Run the status command to see coverage (`--lang` defaults to `ko`):

   ```bash
   cd website && npm run translate -- --lang ko status
   ```

2. **Pick a TM file** in `website/i18n/tm/{lang}/` (e.g. `ko`) and open it in your editor. Each TM file looks like this:

   ```yaml
   meta:
     contributors:
       - alice
   entries:
     - source: "What is Actionbase?"
       target: "" # ← fill in your translation here
       context: heading
     - source: "Actionbase is a database for serving user interactions."
       target: "" # ← and here
       context: paragraph
   ```

3. **Fill in translations.** Find entries with `target: ""` and add the translated text.

4. **Add your GitHub username** to `meta.contributors`.

5. **(Optional) Preview locally.** Build the translated docs and check the output:

   ```bash
   cd website && npm run translate -- --lang ko build
   ```

6. **Open a PR.** Please submit **one section (folder) per PR** rather than translating all pages at once — this keeps reviews manageable and allows incremental progress.

See [TRANSLATION.md](TRANSLATION.md) for technical details and TM format.

## How we collaborate

We collaborate through [GitHub](https://github.com/kakao/actionbase):

- **[Discussions](https://github.com/kakao/actionbase/discussions)**: Questions, ideas, and open-ended conversations
- **[Issues](https://github.com/kakao/actionbase/issues)**: Bug reports, feature requests, and concrete improvements
- **[Pull Requests](https://github.com/kakao/actionbase/pulls)**: Code and documentation changes

For questions, ideas, or feedback, join us on [Discussions](https://github.com/kakao/actionbase/discussions).

Pull requests are reviewed collaboratively and merged by Maintainers.

When submitting a pull request, please sign the [CLA](https://cla-assistant.io/kakao/actionbase) (Contributor Licensing Agreement) for Individual. If you need a Contributor Licensing Agreement for Corporate, please [contact us](mailto:oss@kakaocorp.com).

## Security

Report security vulnerabilities through [GitHub Security Advisories](https://github.com/kakao/actionbase/security/advisories/new) instead of opening a public issue.

## Community standards

All contributors are expected to follow the **[Code of Conduct](https://github.com/kakao/actionbase/blob/main/CODE_OF_CONDUCT.md)**.

For project roles, see [Governance](GOVERNANCE.md).
