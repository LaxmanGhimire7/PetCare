# Applies a PetCare kit update (.docx) straight into your existing Android project.
#   python apply_kit.py PetCare_Kit_v4.docx            -> dry run: lists what would change
#   python apply_kit.py PetCare_Kit_v4.docx --apply    -> writes the files
# Never deletes anything. Each file it replaces is first copied to .petcare-kit/backup/<time>/.
# It finds your package from where TodayScreenBinder.kt already lives. Standard library only.
import argparse, base64, datetime, html, os, re, shutil, subprocess, sys, zipfile

KIT_PKG, KIT_R, KIT_JAVA = "com.petcare.design", "com.petcare.R", "app/src/main/java/com/petcare/design/"

def read_docx(path):
    xml = zipfile.ZipFile(path).read("word/document.xml").decode("utf-8")
    files, current = {}, None
    for para in re.findall(r"<w:p[ >].*?</w:p>", xml, re.S):
        m = re.search(r'<w:pStyle w:val="([^"]+)"', para)
        style = m.group(1) if m else ""
        text = html.unescape("".join(re.findall(r"<w:t(?: [^>]*)?>(.*?)</w:t>", para, re.S)))
        if style == "Heading2" and re.match(r"(app|licenses)/|INTEGRATION_V\d+\.md$", text.strip()):
            current = text.strip()
            files[current] = []
        elif style in ("Code", "FontData") and current:
            files[current].append(text)
        elif style.startswith("Heading"):
            current = None
    return {p: (base64.b64decode("".join(l)) if p.endswith(".ttf") else "\n".join(l) + "\n") for p, l in files.items()}

def locate_kit(root, package):
    for base in ("app/src/main/java", "app/src/main/kotlin"):
        top = os.path.join(root, base)
        if package:
            if os.path.isdir(os.path.join(top, *package.split("."))):
                return base, package
            continue
        hits = [d for d, _, fs in os.walk(top) if "TodayScreenBinder.kt" in fs]
        if len(hits) == 1:
            return base, os.path.relpath(hits[0], top).replace(os.sep, ".")
        if len(hits) > 1:
            sys.exit("Found TodayScreenBinder.kt in more than one place. Re-run with --package your.app.design")
    if package:
        return "app/src/main/java", package
    sys.exit("Couldn't find the design kit (TodayScreenBinder.kt) in app/src/main. Re-run with --package your.app.design")

def r_package(root, kit_dir):
    today = os.path.join(root, kit_dir, "TodayScreenBinder.kt")
    if os.path.exists(today):
        m = re.search(r"^import\s+([\w.]+)\.R\s*$", open(today, encoding="utf-8").read(), re.M)
        if m:
            return m.group(1)
    for name in ("app/build.gradle.kts", "app/build.gradle"):
        p = os.path.join(root, name)
        if os.path.exists(p):
            m = re.search(r"namespace\s*=?\s*[\"']([\w.]+)[\"']", open(p, encoding="utf-8").read())
            if m:
                return m.group(1)
    sys.exit("Couldn't work out your app's namespace (for the R import). Check app/build.gradle.kts has namespace = \"...\"")

def git_dirty(root):
    # Only tracked files count: the .docx and this script are expected to be untracked.
    try:
        out = subprocess.run(["git", "status", "--porcelain", "--untracked-files=no"], cwd=root, capture_output=True, text=True)
        return out.returncode == 0 and out.stdout.strip() != ""
    except OSError:
        return False

def main():
    ap = argparse.ArgumentParser(description="Apply a PetCare kit update into this project.")
    ap.add_argument("docx")
    ap.add_argument("--project", default=".", help="project root (the folder containing app/)")
    ap.add_argument("--package", help="package the design kit lives in, e.g. com.example.petcare.design")
    ap.add_argument("--apply", action="store_true", help="write the files (default is a dry run)")
    ap.add_argument("--force", action="store_true", help="apply even with uncommitted changes")
    a = ap.parse_args()

    root = os.path.abspath(a.project)
    if not os.path.isdir(os.path.join(root, "app")):
        sys.exit("Run this from your project root (the folder that contains app/).")
    files = read_docx(a.docx)
    base, pkg = locate_kit(root, a.package)
    kit_dir = os.path.join(base, *pkg.split("."))
    rpkg = r_package(root, kit_dir)
    print(f"Design kit package: {pkg}\nR import:           {rpkg}.R\n")

    plan = []
    for kit_path, content in sorted(files.items()):
        if kit_path.startswith(KIT_JAVA):
            dest = os.path.join(kit_dir, kit_path[len(KIT_JAVA):])
        elif kit_path.startswith("app/"):
            dest = kit_path
        else:
            dest = os.path.join(".petcare-kit", kit_path)
        if isinstance(content, str):
            content = (content.replace("package " + KIT_PKG, "package " + pkg)
                              .replace("import " + KIT_R, "import " + rpkg + ".R")
                              .replace(KIT_PKG + ".", pkg + "."))
            data = content.encode("utf-8")
        else:
            data = content
        full = os.path.join(root, dest)
        state = "NEW" if not os.path.exists(full) else ("SAME" if open(full, "rb").read() == data else "UPDATE")
        plan.append((state, dest, full, data))

    for state, dest, _, _ in plan:
        print(f"  {state:<7}{dest}")
    counts = {s: sum(1 for p in plan if p[0] == s) for s in ("NEW", "UPDATE", "SAME")}
    print(f"\n{counts['NEW']} new, {counts['UPDATE']} updated, {counts['SAME']} unchanged. Nothing is deleted.")

    if not a.apply:
        print("\nDry run only. Add --apply to write these files.")
        return
    if git_dirty(root) and not a.force:
        sys.exit("\nYou have uncommitted changes. Commit them first (so git can undo this), or add --force.")

    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    for state, dest, full, data in plan:
        if state == "SAME":
            continue
        if state == "UPDATE":
            backup = os.path.join(root, ".petcare-kit", "backup", stamp, dest)
            os.makedirs(os.path.dirname(backup), exist_ok=True)
            shutil.copy2(full, backup)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "wb") as f:
            f.write(data)

    gitignore = os.path.join(root, ".gitignore")
    existing = open(gitignore, encoding="utf-8").read() if os.path.exists(gitignore) else ""
    if ".petcare-kit/" not in existing:
        with open(gitignore, "a", encoding="utf-8") as f:
            f.write(("" if existing.endswith("\n") or not existing else "\n") + ".petcare-kit/\n")

    print(f"\nDone. Backups of replaced files: .petcare-kit/backup/{stamp}/")
    guides = sorted(p for p in files if p.startswith("INTEGRATION_"))
    print("Next: read .petcare-kit/" + (guides[-1] if guides else "INTEGRATION.md") + " and follow it.")

if __name__ == "__main__":
    main()
