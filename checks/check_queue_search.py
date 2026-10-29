"""Run after :app:assembleDebug; uses its cached Kotlin compiler, no test dependencies."""
import os
from pathlib import Path
import subprocess
import tomllib
import zipfile

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
model = next((root / "app/build").rglob("paige/navic/domain/model/DomainSong.class"), None)
if model is None:
    raise SystemExit("Build Android first; DomainSong.class is missing.")
classes = model.parents[4]
out = root / "build/queue-search-check"
out.mkdir(parents=True, exist_ok=True)
def android_jar(group, artifact):
    aar = max((cache / group / artifact).rglob("*.aar"), key=lambda p: p.stat().st_mtime)
    target = out / (artifact + ".jar")
    with zipfile.ZipFile(aar) as archive:
        target.write_bytes(archive.read("classes.jar"))
    return str(target)

classpath = os.pathsep.join([str(classes), compiler[1],
    android_jar("androidx.room3", "room3-runtime-android"),
    android_jar("androidx.sqlite", "sqlite-android"),
    android_jar("androidx.paging", "paging-common-android"),
    jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm"),
    jar("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm")])
source = root / "app/src/main/kotlin/paige/navic/ui/screen/queue/QueueSearch.kt"
subprocess.run(["java", "-cp", os.pathsep.join(compiler), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
    "-no-stdlib", "-no-reflect", "-classpath", classpath, "-d", str(out),
    str(source), str(root / "checks/QueueSearchCheck.kt")], check=True)
subprocess.run(["java", "-cp", os.pathsep.join([str(out), classpath]),
    "paige.navic.ui.screen.queue.QueueSearchCheckKt"], check=True)
