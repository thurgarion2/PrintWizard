import json

def events_are_closed(trace):
    stack = []
    for label in trace:
        labelPos = label[0]
        nodeId = label[2]
        
        if labelPos == 'start':
            stack.append(nodeId)
        if labelPos == 'end':
            id = stack.pop()
            if nodeId!=id:
                print(f"error event {id} should be closed but event {nodeId} is closed")
        
    
    if len(stack)>0:
        print(f"following {stack} events should be closed")

if __name__ == '__main__':
    with open('eventTrace.json') as f:
        trace = json.load(f)['trace']
        events_are_closed(trace)
       