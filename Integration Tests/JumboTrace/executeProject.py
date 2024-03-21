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
    process = subprocess.Popen(cmd, shell=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout, stderr = process.communicate()
    if stdout and display:
        print(stdout)
    if stderr:
        print(stderr)


if __name__ == "__main__":
    excluded = ["Chemistry", "Casts", "Nulls", "CaughtExceptions"]

    if len(sys.argv) < 1:
        print("Provide path to agent")
        sys.exit(1)
    agentPath = sys.argv[1]
    examples = Path("./JumboTrace/examples")

    for example in filter(lambda x: x.name not in excluded,examples.iterdir()):
        print(f"======= {example.name} ========")
        executeExample(str(example), agentPath)

