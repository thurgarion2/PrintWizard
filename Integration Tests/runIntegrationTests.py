import os
import subprocess

def format_output(output):
    return '\n'.join(output.split('\n'))

def command(cmd : str, display=True):
    process = subprocess.Popen(cmd, shell=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout, stderr = process.communicate()
    if stdout and display:
        print(stdout)
    if stderr:
        print(stderr)

if __name__ == "__main__":
    ##command("./JumboTrace/initDirectory.sh")
    os.chdir("./JumboTrace")
    command("python3 ./executeProject.py ../../RuntimeAgent/target/RuntimeAgent-1.0-SNAPSHOT.jar")