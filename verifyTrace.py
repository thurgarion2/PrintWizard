import json
from enum import Enum

tracePath = './IntegrationTest/examples/Boids/eventTrace.json'

EventGroupKind =  Enum('EventGroupKind', ['controlFlow', 'statement', 'subStatement'])
class EventGroup:
    
    def __init__(self, children, kind) -> None:
        self.children = children
        self.kind = kind

def isGroupEvent(json):
    return json['type'] == 'GroupEvent'

def eventTree(startIdx, labels):
    label = labels[startIdx]
    assert(label['pos']=='start')
    children = []
    
    index = startIdx + 1
    label = labels[index]
    while label['pos'] != 'end':
        (endIndex, event) =  eventTree(index, labels)
        index = endIndex + 1
        label = labels[index]
        children.append(event)
        

    return (index, EventGroup(children, EventGroupKind[label['eventType']]))

def verifyFlow(event):
    if(event.kind!=EventGroupKind.controlFlow):
        return False
    
    for child in event.children:
        if(child.kind==EventGroupKind.controlFlow):
            return verifyFlow(child)
        if(child.kind==EventGroupKind.statement):
            return verifyStatement(child)
        return False
    return True

def verifyStatement(event):
    if(event.kind!=EventGroupKind.statement):
        return False
    
    for child in event.children:
        if(child.kind==EventGroupKind.controlFlow):
            return verifyFlow(child)
        if(child.kind==EventGroupKind.subStatement):
            return verifySubstatement(child)
        return False
    return True
    
def verifySubstatement(event):
    if(event.kind!=EventGroupKind.subStatement):
        return False
    
    for child in event.children:
        if(child.kind==EventGroupKind.controlFlow):
            return verifyFlow(child)
        return False
    return True

with open(tracePath) as f:
    data = json.load(f)
    tree = eventTree(0, list(filter(isGroupEvent, data['trace'])))[1]
    print(verifyFlow(tree))
    
    
    
    
    
