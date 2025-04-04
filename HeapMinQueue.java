package graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A min priority queue of distinct elements of type `KeyType` associated with (extrinsic) integer
 * priorities, implemented using a binary heap paired with a hash table.
 */
public class HeapMinQueue<KeyType> implements MinQueue<KeyType> {

    /**
     * Pairs an element `key` with its associated priority `priority`.
     */
    private record Entry<KeyType>(KeyType key, int priority) {
        // Note: This is equivalent to declaring a static nested class with fields `key` and
        //  `priority` and a corresponding constructor and observers, overriding `equals()` and
        //  `hashCode()` to depend on the fields, and overriding `toString()` to print their values.
        // https://docs.oracle.com/en/java/javase/17/language/records.html
    }

    /**
     * Associates each element in the queue with its index in `heap`.  Satisfies
     * `heap.get(index.get(e)).key().equals(e)` if `e` is an element in the queue. Only maps
     * elements that are in the queue (`index.size() == heap.size()`).
     */
    private final Map<KeyType, Integer> index;

    /**
     * Sequence representing a min-heap of element-priority pairs.  Satisfies
     * `heap.get(i).priority() >= heap.get((i-1)/2).priority()` for all `i` in `[1..heap.size()]`.
     */
    private final ArrayList<Entry<KeyType>> heap;

    /**
     * Assert that our class invariant is satisfied.  Returns true if it is (or if assertions are
     * disabled).
     */
    private boolean checkInvariant() {
        for (int i = 1; i < heap.size(); ++i) {
            int p = (i - 1) / 2;
            assert heap.get(i).priority() >= heap.get(p).priority();
            assert index.get(heap.get(i).key()) == i;
        }
        assert index.size() == heap.size();
        return true;
    }

    /**
     * Create an empty queue.
     */
    public HeapMinQueue() {
        index = new HashMap<>();
        heap = new ArrayList<>();
        assert checkInvariant();
    }

    /**
     * Return whether this queue contains no elements.
     */
    @Override
    public boolean isEmpty() {
        return heap.isEmpty();
    }

    /**
     * Return the number of elements contained in this queue.
     */
    @Override
    public int size() {
        return heap.size();
    }

    /**
     * Return an element associated with the smallest priority in this queue.  This is the same
     * element that would be removed by a call to `remove()` (assuming no mutations in between).
     * Throws NoSuchElementException if this queue is empty.
     */
    @Override
    public KeyType get() {
        // Propagate exception from `List::getFirst()` if empty.
        return heap.getFirst().key();
    }

    /**
     * Return the minimum priority associated with an element in this queue.  Throws
     * NoSuchElementException if this queue is empty.
     */
    @Override
    public int minPriority() {
        return heap.getFirst().priority();
    }

    /**
     * If `key` is already contained in this queue, change its associated priority to `priority`.
     * Otherwise, add it to this queue with that priority.
     */
    @Override
    public void addOrUpdate(KeyType key, int priority) {
        if (!index.containsKey(key)) {
            add(key, priority);
        } else {
            update(key, priority);
        }
    }

    /**
     * Remove and return the element associated with the smallest priority in this queue.  If
     * multiple elements are tied for the smallest priority, an arbitrary one will be removed.
     * Throws NoSuchElementException if this queue is empty.
     */
    @Override
    public KeyType remove() {

        if (heap.isEmpty()){
            throw new NoSuchElementException();
        }

        KeyType toReturn = heap.getFirst().key();
        index.remove(toReturn);

        if (heap.size() == 1){
            return heap.removeLast().key();
        }

        heap.set(0, heap.removeLast());
        bubbleDown(0);

        return toReturn;
    }

    /**
     * Remove all elements from this queue (making it empty).
     */
    @Override
    public void clear() {
        index.clear();
        heap.clear();
        assert checkInvariant();
    }

    /**
     * Swap the Entries at indices `i` and `j` in `heap`, updating `index` accordingly.  Requires `0
     * <= i,j < heap.size()`.
     */
    private void swap(int i, int j) {
        assert i >= 0 && i < heap.size();
        assert j >= 0 && j < heap.size();

        Entry<KeyType> iEntry = heap.get(i);
        Entry<KeyType> jEntry = heap.get(j);

        index.replace(iEntry.key(), j);
        index.replace(jEntry.key(), i);

        heap.set(i, jEntry);
        heap.set(j, iEntry);

    }

    /**
     * Add element `key` to this queue, associated with priority `priority`.  Requires `key` is not
     * contained in this queue.
     */
    private void add(KeyType key, int priority) {

        assert !index.containsKey(key);

        Entry<KeyType> toAdd = new Entry<KeyType>(key, priority);

        heap.addLast(toAdd);
        bubbleUp(heap.size() - 1);

        index.put(key, heap.indexOf(toAdd));

        assert checkInvariant();
    }

    /**
     * Change the priority associated with element `key` to `priority`.  Requires that `key` is
     * contained in this queue.
     */
    private void update(KeyType key, int priority) {
        assert index.containsKey(key);

        Entry<KeyType> changed = new Entry<KeyType>(key, priority);
        int prevPriority = heap.get(index.get(key)).priority();

        heap.set(index.get(key), changed);

        if (prevPriority > priority){
            bubbleUp(index.get(key));
        }else{
            bubbleDown(index.get(key));
        }

        index.replace(key, heap.indexOf(changed));

        assert checkInvariant();
    }

    /**
     * Bubble element at index `k` up in heap to its right place.
     * Precondition: Every `values[i]` â‰¤ its parent except perhaps for `values[k]`.
     */
    private void bubbleUp(int bubble) {

        int parent = (bubble - 1) / 2;
        while (bubble > 0 && heap.get(bubble).priority() < heap.get(parent).priority()) {
            swap(bubble, parent);
            bubble = parent;
            parent = (bubble - 1) / 2;
        }
    }

    /**
     * Bubble element at index `k` down in heap to its right place.
     * If the two children have the same priority, bubble down the left one.
     * Precondition: {@code 0 <= k < size} and
     *   Each `values[i]` â‰¥ its children except perhaps for `values[k]`.
     */
    private void bubbleDown(int bubble) {
        int child = 2*bubble + 1;
        while (child < heap.size()) {
            if (child + 1 < heap.size() && heap.get(child + 1).priority() < heap.get(child).priority()) {
                child += 1;
            }
            if (heap.get(bubble).priority() <= heap.get(child).priority()) {
                return;
            }
            swap(bubble, child);
            bubble = child;
            child = 2*bubble + 1;
        }
    }

}
