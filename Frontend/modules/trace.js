export { fromCsv };
import { 
    parseEvent, 
    eventId,
    kind,
    eventKinds,
    nodeId,
    assignValue,
    result
 } from "./events.js";


const fromCsv = function(text){
    const events = text
        .split('\n')
        .filter(fields => fields.length>0)
        .map(parseEvent);

    return new TraceTree(events, 0, events.length-1);    
}

//immutable datastructure
class TraceTree {

    constructor(events, startIndex, endIndex){
        this.events = events;

        console.assert(startIndex>=0 && startIndex<=endIndex && endIndex<events.length);
        this.startIndex = startIndex;
        this.endIndex = endIndex;

        console.assert(eventId(events[startIndex])==eventId(events[endIndex]));
        this.eventsId = eventId(events[startIndex]);

        this.coverEvents = this.events.slice(startIndex, endIndex+1);
        this.children_ = undefined;
    }

    print(prefix=''){
        const event = this.event().start;
        let text = prefix+nodeId(event)+'<br />';
       
        for(const child of this.children()){
            text+=child.print(prefix+'|-');
           
        }
        
        return text;
    }

    children(){
        if(this.children_==undefined){
            this.children_ = [];
            let index = this.startIndex+1;
            while(index<this.endIndex){
                const endIndex = eventEndIndex(this.events, index);
               
                this.children_.push(new TraceTree(this.events, index, endIndex));
                index = endIndex+1;
            }
        }

        return this.children_;
    }

    event(){
        return new TraceEvent(this.events[this.startIndex], this.events[this.endIndex]);
    }

    events(){
        //should replace it an implementation that don't copy
        return this.coverEvents;
    }
}

class TraceEvent {
    constructor(start, end){
        this.start=start;
        this.end=end;
    }

    eventId(){
        return eventId(this.start);
    }
    nodeId(){
        return nodeId(this.start);
    }
    kind(){
        return kind(this.start);
    }
    result(){    
        return result(this.end);
    }
    assignValue(){
        return assignValue(this.start);
    }
}

const eventEndIndex = function(events, startIndex){
    console.assert(startIndex>=0 && startIndex<events.length);

    const event = events[startIndex];
    if(kind(event)==eventKinds.update)
        return startIndex;

    const eventId_ = eventId(event);
    for(let index=startIndex+1; index<events.length; ++index ){
        if(eventId(events[index])==eventId_){
            return index;
        }
    }
    console.assert(false);
    return undefined;
}