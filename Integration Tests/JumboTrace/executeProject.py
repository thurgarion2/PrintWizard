import sys
import subprocess
from pathlib import Path

def main():
    # Check if arguments are provided
    if len(sys.argv) < 2:
        print("Usage: python script.py <argument>")
        sys.exit(1)  # Exit with error code 1

    # Access the argument(s)
    argument = sys.argv[1]

    # Your program logic here
    print("Argument provided:", argument)

def executeExample(path : str, agentPath : str):
    command(f"javac -g {path}/*.java")
    command(f"java -javaagent:{agentPath} -cp {path} Main", False)

def command(cmd : str, display=True):
    process = subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout, stderr = process.communicate()
    if stdout and display:
        print(stdout)
    if stderr:
        print(stderr)


if __name__ == "__main__":
    if len(sys.argv) < 1:
        print("Provide path to agent")
        sys.exit(1)
    agentPath = sys.argv[1]
    examples = Path("./JumboTrace/examples")

    for example in examples.iterdir():
        executeExample(str(example), agentPath)

