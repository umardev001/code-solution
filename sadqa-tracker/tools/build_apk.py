#!/usr/bin/env python3
"""Build Sadqa Tracker APK without the Android SDK:
javac (android-all.jar) -> dx -> hand-made binary manifest -> zip -> jarsigner (v1)."""
import os, subprocess, sys, zipfile, shutil, glob
sys.path.insert(0, os.path.dirname(__file__))
import arsc
from axml import Node, encode, STR, INT, BOOL, REF

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
LIBS = os.environ.get('ST_LIBS', '/tmp/claude-0/apk/dl')
WORK = os.environ.get('ST_WORK', '/tmp/claude-0/apk/build-sadqa')
OUT = os.path.join(ROOT, 'SadqaTracker.apk')
PKG = 'com.sadqa.tracker'


def run(cmd, **kw):
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    err = '\n'.join(l for l in (r.stdout + r.stderr).splitlines() if 'JAVA_TOOL_OPTIONS' not in l)
    if r.returncode:
        sys.exit('FAILED: %s\n%s' % (' '.join(cmd), err))
    return err


def manifest(icon_res=None):
    a = lambda n, k, v: ('android:' + n, k, v)
    # No permissions at all: no INTERNET, no storage, nothing.
    app_attrs = [a('label', STR, 'Sadqa Tracker'), a('allowBackup', BOOL, False), a('theme', REF, 0x0103006c)]
    if icon_res:
        app_attrs.append(a('icon', REF, icon_res))
    login = Node('activity', [a('name', STR, '.LoginActivity'), a('exported', BOOL, True), a('label', STR, 'Sadqa Tracker'),
                              a('screenOrientation', INT, 1)], [
        Node('intent-filter', [], [Node('action', [a('name', STR, 'android.intent.action.MAIN')]),
                                   Node('category', [a('name', STR, 'android.intent.category.LAUNCHER')])])])
    main = Node('activity', [a('name', STR, '.MainActivity'), a('exported', BOOL, False), a('label', STR, 'Sadqa Tracker'),
                             a('screenOrientation', INT, 1)])
    app = Node('application', app_attrs, [login, main])
    root = Node('manifest', [('package', STR, PKG), a('versionCode', INT, 2), a('versionName', STR, '1.1')],
                [Node('uses-sdk', [a('minSdkVersion', INT, 23), a('targetSdkVersion', INT, 28)]), app])
    return encode(root)


def main():
    shutil.rmtree(WORK, ignore_errors=True)
    cls, out = os.path.join(WORK, 'classes'), os.path.join(WORK, 'out')
    os.makedirs(cls); os.makedirs(out)
    srcs = glob.glob(os.path.join(ROOT, 'src/**/*.java'), recursive=True)
    run(['javac', '-encoding', 'UTF-8', '--release', '8', '-Xlint:-options', '-cp', LIBS + '/android-all.jar', '-d', cls] + srcs)
    run(['java', '-cp', LIBS + '/dx.jar', 'com.android.dx.command.Main', '--dex', '--min-sdk-version=23',
         '--output=' + out + '/classes.dex', cls])
    unsigned = os.path.join(WORK, 'unsigned.apk')
    with zipfile.ZipFile(unsigned, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('AndroidManifest.xml', manifest(ICON_RES))
        z.write(out + '/classes.dex', 'classes.dex')
        z.write(os.path.join(HERE, 'ic_launcher.png'), 'res/drawable/ic_launcher.png')
        z.writestr(zipfile.ZipInfo('resources.arsc'), arsc.build(), zipfile.ZIP_STORED)
    ks = os.path.join(ROOT, 'sadqa.keystore')
    if not os.path.exists(ks):
        run(['keytool', '-genkeypair', '-keystore', ks, '-storepass', 'android', '-keypass', 'android', '-alias', 'sadqa',
             '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=Sadqa Tracker'])
    run(['jarsigner', '-keystore', ks, '-storepass', 'android', '-keypass', 'android', '-sigalg', 'SHA256withRSA',
         '-digestalg', 'SHA-256', '-signedjar', OUT, unsigned, 'sadqa'])
    print('built', OUT, os.path.getsize(OUT), 'bytes')

ICON_RES = 0x7f010000

if __name__ == '__main__':
    main()
