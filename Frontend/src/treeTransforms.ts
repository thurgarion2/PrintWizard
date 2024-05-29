export {
    scopedContextTransform,
    EventWithContext
};
import {
    Event,
    EventKind,
    EventKindTypes,
    ExecutionStep
} from "./event";

/*******************************************************
 **************** transform ******************
 *******************************************************/

/**************
 ********* types
 **************/

type ExecutionWithContext<Context, State> = EventWithContext<Context, State> | ExecutionStep


// an event is an ordered list of execution steps
// an event represent a logical group of steps
interface EventWithContext<Context, State> {
    type: 'Event',
    context: Context, 
    state: State,
    // the ordered list of executions steps 
    executions: () => ExecutionWithContext<Context, State>[],
    kind: EventKind,
}

/**************
 ********* implementation
 **************/

function scopedContextTransform<Context, State>(
    root: Event,
    rootContext: Context,
    aggregate: (event: Event, state: State[]) => State,
    propagate: (event: Event, ctx: Context) => Context
): EventWithContext<Context, State> {

    function helper(event: Event, context: Context): EventWithContext<Context, State> {
        let children : ExecutionWithContext<Context, State>[] = []
        let childStates : State[] = []
        let childContext = propagate(event, context)

        event.executions().forEach(child => {
            switch (child.type) {
                case 'ExecutionStep':
                    children.push(child)
                    break;
                case 'Event':
                    let childWithContext = helper(child,childContext)
                    children.push(childWithContext)

                    switch (child.kind.type) {
                        // we don't pass state through change of control flow
                        case EventKindTypes.Flow:
                            break;
                        default:
                            childStates.push(childWithContext.state)
                    }
            }
        })

        return {
            type: 'Event',
            context: context, 
            state: aggregate(event, childStates),
            // the ordered list of executions steps 
            executions: () => children,
            kind: event.kind,
        }
    }

    return helper(root, rootContext);
}