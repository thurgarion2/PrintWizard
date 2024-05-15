export {
    scopedTreeTransform,
    ScopedLabel
};
import {
    EVENT,
    Label,
    LabelPos,
    EventKind
} from "./event";

/*******************************************************
 **************** transform ******************
 *******************************************************/
type ScopedLabel<Context, State> = { label: Label, context: Context, state: { store : State} }

function scopedTreeTransform<Context, State>(
    labels: Label[],
    rootContext: Context,
    aggregate: (event: EVENT, state: State[]) => State,
    propagate: (event: EVENT, ctx: Context) => Context
): ScopedLabel<Context, State>[] {
    let output: { label: Label, context: Context, state:  { store : State} }[] = []

    function scopedTreeTransformHelper(
        context: Context,
        treeStartIndex: number
    ): [State[], number] {
        const label = labels[treeStartIndex]
        let state : State 

        switch (label.pos) {
            case LabelPos.UPDATE:
                state = aggregate(label.event, [])
                output.push({ label: label, context: context, state: {store : state} });
                return [[state], treeStartIndex];
            case LabelPos.START:
                const nextContext = propagate(label.event, context)
                let s  = { store : undefined as State}
                output.push({ label: label, context: context, state: s });

                let index = treeStartIndex + 1
                let childStates = []
                
                consume: while(true){
                    switch(labels[index].pos){
                        case LabelPos.END:
                            output.push({ label: labels[index], context: context, state: s });
                            break consume;
                        case LabelPos.CALL:
                            output.push({ label: labels[index], context: context, state: s });
                            index = index + 1;
                            break;
                        case LabelPos.START:
                        case LabelPos.UPDATE:
                            let [state, endIdx] = scopedTreeTransformHelper(nextContext, index)
                            index = endIdx + 1
                            childStates.push(...state)
                    }
                }
                state = aggregate(label.event, childStates)
                s['store'] = state as State

                
                return [ label.event.description.type.kind === EventKind.Flow ? [] : [state], index]
            case LabelPos.CALL:
            case LabelPos.END:
                console.log("label end without label start")
                throw new Error()
        }
    }

    scopedTreeTransformHelper(rootContext, 0)
    console.assert(output.length===labels.length, "we are missing some labels")

    return output;
}