import { varName, value } from "./events.js";
export {viewEvent, viewEventWithAction, htmlElement, brElement}

const viewEvent = function (identation, text, updates, result) {
    return {
        ident: identation,
        text: text,
        updates: updates,
        result: result,
        action: undefined
    }
}

const viewEventWithAction = function (identation, text, updates, result, action) {
    return {
        ident: identation,
        text: text,
        updates: updates,
        result: result,
        action: action
    }
}

const htmlElement = function (viewEvent) {
    const mainText = identation(viewEvent.ident) + viewEvent.text;
    const elements = [
        textElement(mainText), 
        inlineStyle(' ->', viewEvent.result, 'result'),
        textElement(' '),
        inlineStyle('', joinAssigns(viewEvent.updates), 'updates')
    ];

    if(viewEvent.action!=undefined){
        return clickAction(elements, viewEvent.action)
    }else{
        return spanElement(elements);
    }
}

const clickAction = function(childrens, action){
    let span = spanElement(childrens);
    span.addEventListener('click', action)
    return span;
}


const inlineStyle = function (prefix, text, cssClass) {
    if(text!=''){
        return spanElement([textElement(prefix + text)], cssClass);
    }else{
        return spanElement([],cssClass);
    }
    
}

const brElement = function(){
    return document.createElement("br");
}

const spanElement = function(childrens, clazz=""){
    let span = document.createElement("span");
    childrens.map(child => span.appendChild(child));
    if(clazz!="")
        span.className = clazz;

    return span;
}

const textElement = function(text){
    return document.createTextNode(text);
}


const joinAssigns = function (assigns) {
    return assigns
        .map(assign => varName(assign) + '=' + value(assign))
        .join(', ');
}

let storeIdent = { 0: '' }
const identation = function (ident) {
    console.assert(ident >= 0)

    if (!(ident in storeIdent)) {
        storeIdent[ident] = '\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0'+ identation(ident - 1);
    }
    return storeIdent[ident];
}