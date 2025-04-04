package selector;

import java.awt.Point;
import java.util.ListIterator;

/**
 * Models a selection tool that connects each added point with a straight line.
 */
public class PointToPointSelectionModel extends SelectionModel {

    enum PointToPointState implements SelectionState {
        /**
         * No selection is currently in progress (no starting point has been selected).
         */
        NO_SELECTION,

        /**
         * Currently assembling a selection.  A starting point has been selected, and the selection
         * path may contain a sequence of segments, which can be appended to by adding points.
         */
        SELECTING,

        /**
         * The selection path represents a closed selection that start and ends at the same point.
         * Points may be moved, but no additional points may be added.  The selected region of the
         * image may be extracted and saved from this state.
         */
        SELECTED;

        @Override
        public boolean isEmpty() {
            return this == NO_SELECTION;
        }

        @Override
        public boolean isFinished() {
            return this == SELECTED;
        }

        @Override
        public boolean canUndo() {
            return this == SELECTED || this == SELECTING;
        }

        @Override
        public boolean canAddPoint() {
            return this == NO_SELECTION || this == SELECTING;
        }

        @Override
        public boolean canFinish() {
            return this == SELECTING;
        }

        @Override
        public boolean canEdit() {
            return this == SELECTED;
        }

        @Override
        public boolean isProcessing() {
            return false;
        }
    }

    /**
     * The current state of this selection model.
     */
    private PointToPointState state;

    /**
     * Create a model instance with no selection and no image.  If `notifyOnEdt` is true, property
     * change listeners will be notified on Swing's Event Dispatch thread, regardless of which
     * thread the event was fired from.
     */
    public PointToPointSelectionModel(boolean notifyOnEdt) {
        super(notifyOnEdt);
        state = PointToPointState.NO_SELECTION;
    }

    /**
     * Create a model instance with the same image and event notification policy as `copy`, and
     * attempt to preserve `copy`'s selection if it can be represented without violating the
     * invariants of this class.
     */
    public PointToPointSelectionModel(SelectionModel copy) {
        super(copy);
        if (copy instanceof PointToPointSelectionModel) {
            state = ((PointToPointSelectionModel) copy).state;
        } else {
            if (copy.state().isEmpty()) {
                assert segments.isEmpty() && controlPoints.isEmpty();
                state = PointToPointState.NO_SELECTION;
            } else if (!copy.state().isFinished() && controlPoints.size() == segments.size() + 1) {
                // Assumes segments start and end at control points
                state = PointToPointState.SELECTING;
            } else if (copy.state().isFinished() && controlPoints.size() == segments.size()) {
                // Assumes segments start and end at control points
                state = PointToPointState.SELECTED;
            } else {
                reset();
            }
        }
    }

    @Override
    public SelectionState state() {
        return state;
    }

    /**
     * Change our selection state to `newState` (internal operation).  This should only be used to
     * perform valid state transitions.  Notifies listeners that the "state" property has changed.
     */
    private void setState(PointToPointState newState) {
        PointToPointState oldState = state;
        state = newState;
        propSupport.firePropertyChange("state", oldState, newState);
    }

    /**
     * Return a straight line segment from our last point to `p`.
     */
    @Override
    public PolyLine liveWire(Point p) {
        assert !controlPoints().isEmpty();

        return new PolyLine(controlPoints().getLast(), p);
    }

    /**
     * Add `p` as the next control point of our selection, extending our selection with a straight
     * line segment from the end of the current selection path to `p`.
     */
    @Override
    protected void appendToSelection(Point p) {

        assert !controlPoints().isEmpty();
        Point pClone = new Point(p.x, p.y);
        if (segments.isEmpty()) {
            segments.add(new PolyLine(controlPoints.getLast(), pClone));
        } else {
            segments.add(new PolyLine(segments.getLast().end(), pClone));
        }

        controlPoints.add(pClone);


    }

    /**
     * Move the control point with index `index` to `newPos`.  The segment that previously
     * terminated at the point should be replaced with a straight line connecting the previous point
     * to `newPos`, and the segment that previously started from the point should be replaced with a
     * straight line connecting `newPos` to the next point (where "next" and "previous" wrap around
     * as necessary). Notify listeners that the "selection" property has changed.
     */
    @Override
    public void movePoint(int index, Point newPos) {
        // Confirm that we have a closed selection and that `index` is valid
        if (!state().canEdit()) {
            throw new IllegalStateException("May not move point in state " + state());
        }
        if (index < 0 || index >= controlPoints.size()) {
            throw new IllegalArgumentException("Invalid point index " + index);
        }

        Point clone = new Point(newPos.x, newPos.y);

        //We use the same ending Point, but we modify the first Point.
        PolyLine newStarting = new PolyLine(clone, segments.get(index).end());

        int newEndingIndex = index - 1;

        //If we get a negative index, we have to wrap around to the largest index.
        if (newEndingIndex < 0) {
            newEndingIndex += segments.size();
        }

        //We use the same starting Point, but we modify the last Point.
        PolyLine newEnding = new PolyLine(segments.get(newEndingIndex).start(), clone);

        segments.set(index, newStarting);
        segments.set(newEndingIndex, newEnding);

        controlPoints.set(index, clone);

        propSupport.firePropertyChange("selection", null, selection());

    }

    public void finishSelection() {
        if (!state.canFinish()) {
            throw new IllegalStateException("Cannot finish a selection that is already finished");
        }
        if (segments.isEmpty()) {
            reset();
        } else {
            addPoint(controlPoints.getFirst());
            // Don't double-add the starting point
            controlPoints.removeLast();
            setState(PointToPointState.SELECTED);
        }
    }

    @Override
    public void reset() {
        super.reset();
        setState(PointToPointState.NO_SELECTION);
    }

    @Override
    protected void startSelection(Point start) {
        super.startSelection(start);
        setState(PointToPointState.SELECTING);
    }

    @Override
    protected void undoPoint() {
        if (segments.isEmpty()) {
            // Reset to remove the starting point
            reset();

        } else {
            if (segments.getLast().end().equals(controlPoints.getFirst())) {
                segments.removeLast();
            } else {
                segments.removeLast();
                controlPoints.removeLast();
            }
            if (state().equals(PointToPointState.SELECTED) || state.isFinished()) {
                setState(PointToPointState.SELECTING);
            }

        }
        propSupport.firePropertyChange("selection", null, selection());

    }
}
