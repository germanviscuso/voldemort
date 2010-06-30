package voldemort.store.db4o;

import java.util.Iterator;

import voldemort.store.PersistenceFailureException;
import voldemort.utils.ClosableIterator;

public class Db4oKeysIterator<Key, Value> implements ClosableIterator<Key> {

    private Iterator<Key> iterator;
    private volatile boolean isOpen;

    public Db4oKeysIterator(Db4oKeyValueProvider<Key, Value> provider) {
        this.iterator = provider.getKeys().iterator();
        isOpen = this.iterator != null;
    }

    /*
     * protected abstract T get(DatabaseEntry key, DatabaseEntry value);
     * 
     * protected abstract void moveCursor(DatabaseEntry key, DatabaseEntry
     * value) throws DatabaseException;
     */

    public final boolean hasNext() {
        return iterator.hasNext();
    }

    public final Key next() {
        if(!isOpen)
            throw new PersistenceFailureException("Call to next() on a closed iterator.");
        return iterator.next();
    }

    public final void remove() {
        throw new UnsupportedOperationException("No removal y'all.");
    }

    public final void close() {
        isOpen = false;
    }

    @Override
    protected final void finalize() {
        if(isOpen) {
            // logger.error("Failure to close cursor, will be forcibly closed.");
            close();
        }

    }

}
