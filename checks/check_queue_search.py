"""Run after :androidApp:assembleDebug; uses its cached Kotlin compiler, no test dependencies."""
import os
from pathlib import Path
import subprocess
import tomllib

root = Path(__file__).resolve().parents[1]
cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
version = tomllib.loads((root / "gradle/libs.versions.toml").read_text(encoding="utf-8"))["versions"]["kotlin"]

def jar(group, artifact, version=None):
    folder = cache / group / artifact
    if version:
        folder /= version
    matches = list(folder.rglob("*.jar"))
    if not matches:
        raise SystemExit(f"Missing cached {artifact}; build Android first.")
    return str(max(matches, key=lambda p: p.stat().st_mtime))

compiler = [jar("org.jetbrains.kotlin", name, version) for name in (
    "kotlin-compiler-embeddable", "kotlin-stdlib", "kotlin-script-runtime",
    "kotlin-daemon-embeddable", "kotlin-build-tools-api")]
compiler += [jar("org.jetbrains.kotlin", "kotlin-reflect"),
             jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm"),
             jar("org.jetbrains", "annotations")]
model = next((root / "composeApp/build").rglob("paige/navic/domain/models/DomainSong.class"), None)
if model is None:
    raise SystemExit("Build Android first; DomainSong.class is missing.")
classes = model.parents[4]
classpath = os.pathsep.join([str(classes), compiler[1],
    jar("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm")])
out = root / "build/queue-search-check"
out.mkdir(parents=True, exist_ok=True)
source = root / "composeApp/src/commonMain/kotlin/paige/navic/ui/screens/queue/QueueSearch.kt"
subprocess.run(["java", "-cp", os.pathsep.join(compiler), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
    "-no-stdlib", "-no-reflect", "-classpath", classpath, "-d", str(out),
    str(source), str(root / "checks/QueueSearchCheck.kt")], check=True)
subprocess.run(["java", "-cp", os.pathsep.join([str(out), classpath]),
    "paige.navic.ui.screens.queue.QueueSearchCheckKt"], check=True)
