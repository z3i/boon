package org.boon.core.value;

import org.boon.core.Value;

import java.util.*;

import static org.boon.Exceptions.die;

/**
 * This class is important to the performance of the parser.
 * It stores Value objects in a map where they are evaluated lazily.
 * This is great for JSONPath types of application, and Object Serialization but not for maps that are going to be stored in a cache.
 *
 * This is because the Value construct is a type of index overlay that merely tracks where the token is located in the buffer,
 * and what if any thing we noted about it (like can be converted to a decimal number, etc.).
 *
 * To mitigate memory leaks this class along with ValueInCharBuf implement two constructs, namely,
 * chop,  and lazyChop.
 *
 * A chop is when we convert backing buffer of a Value object into a smaller buffer.
 * A lazyChop is when we do a chop but only when a get operation is called.
 *
 * The lazyChop is performed on the tree that is touched by the JSONPath expression or its ilk.
 *
 * The chop operation can be done during parsing or lazily by storing the values in this construct.
 *
 */
public class LazyValueMap extends AbstractMap<String, Object> implements ValueMap <String, Object> {

    /** holds the map that gets lazily created on first access. */
    private Map<String, Object> map = null;
    /** holds the list of items that we are managing. */
    private  Entry<String, Value>[] items;
    /** Holds the current number mapping managed by this map. */
    private int len = 0;
    /** Holds whether or not we ae in lazy chop mode or not. */
    private final boolean lazyChop;

    /** Keep track if this map has already been chopped so we don't waste time trying to chop it again. */
    boolean mapChopped = false;





    public LazyValueMap( boolean lazyChop ) {

        this.items = new Entry[ 5 ];
        this.lazyChop = lazyChop;
    }

    public LazyValueMap( boolean lazyChop, int initialSize ) {
        this.items = new Entry[ initialSize ];
        this.lazyChop = lazyChop;
    }

    /** Adds a new MapItemValue to the mapping.
     *
     * @param miv miv we are adding.
     */
    public final void add( MapItemValue miv ) {
        if ( len >= items.length ) {
            items = org.boon.Arrays.grow( items );
        }
        items[ len ] = miv;
        len++;

    }


    /** Gets the item by key from the mapping.
     *
     * @param key to lookup
     * @return
     */
    @Override
    public final Object get( Object key ) {

//        /* If the length is under 5 and we are asking for the key, then just look for the key. Don't build the map. */
//        if ( map == null && items.length < 5 ) {
//            for ( Object item : items ) {
//                MapItemValue miv = ( MapItemValue ) item;
//                if ( key.equals ( miv.name.toValue () ) ) {
//                    object = miv.value.toValue ();
//                }
//            }
//        } else {
//        }

        Object object=null;

        /* if the map is null, then we create it. */
        if ( map == null ) {
                buildMap ();
        }
        object = map.get ( key );

        lazyChopIfNeeded ( object );
        return object;
    }

    /** If in lazy chop mode, and the object is a Lazy Value Map or a ValueList
     * then we force a chop operation for each of its items. */
    private void lazyChopIfNeeded( Object object ) {
        if ( lazyChop ) {
            if ( object instanceof LazyValueMap ) {
                LazyValueMap m = ( LazyValueMap ) object;
                m.chopMap();
            } else if ( object instanceof ValueList ) {
                ValueList list = ( ValueList ) object;
                list.chopList();
            }
        }

    }

    /** Chop this map.
     *
     */
    final void chopMap() {
        /* if it has been chopped then you have to return. */
        if ( mapChopped ) {
            return;
        }
        mapChopped = true;


        /* If the internal map was not create yet, don't. We can chop the value w/o creating the internal map.*/
        if ( this.map == null ) {
            for ( int index = 0; index < len; index++ ) {
                MapItemValue entry = ( MapItemValue ) items [index] ;

                Value value = entry.getValue();
                if ( value == null ) continue;
                if ( value.isContainer() ) {
                    chopContainer( value );
                } else {
                    value.chop();
                }
            }
        } else {
            /* Iterate through the map and do the same thing. Make sure children and children of children are chopped.  */
            for (Map.Entry <String, Object> entry : map.entrySet ()) {

                Object object = entry.getValue();
                if ( object instanceof  Value ) {
                    Value value = (Value) object;
                    if ( value.isContainer() ) {
                        chopContainer( value );
                    } else {
                        value.chop();
                    }
                } else if (object instanceof LazyValueMap) {
                    LazyValueMap m = ( LazyValueMap ) object;
                    m.chopMap();
                } else if ( object instanceof ValueList ) {
                    ValueList list = ( ValueList ) object;
                    list.chopList();
                }
            }
        }

    }

    /* We need to chop up this child container. */
    private void chopContainer( Value value ) {
        Object obj = value.toValue();
        if ( obj instanceof LazyValueMap ) {
            LazyValueMap map = ( LazyValueMap ) obj;
            map.chopMap();
        } else if ( obj instanceof ValueList ) {
            ValueList list = ( ValueList ) obj;
            list.chopList();
        }
    }


    @Override
    public Value put( String key, Object value ) {
        die( "Not that kind of map" );
        return null;
    }


    @Override
    public Set<Entry<String, Object>> entrySet() {
        if ( map == null ) {
            return new FakeSet( items, len );
        } else {
            return map.entrySet();
        }
    }

    private final void buildMap() {

        map = new HashMap<>( items.length );

        for ( Entry<String, Value> miv : items ) {
            if ( miv == null ) {
                break;
            }
            map.put( miv.getKey(), miv.getValue().toValue() );
        }

        items = null;
    }


    public Collection<Object> values() {
        if ( map == null ) buildMap();
        return map.values();
    }


    public int size() {

        if ( map == null ) {
            return len;
        } else {
            return map.size();
        }
    }

    public String toString() {

        if (map == null) buildMap();
        return map.toString();

    }


    /** Simulates the interface of a Set but does not guarantee uniqueness of entries. */
    private static class FakeSet extends AbstractSet<Entry<String, Object>> {
        private final int size;

        @Override
        public <T> T[] toArray( T[] a ) {
            return ( T[] ) items;
        }

        Entry<String, Value>[] items;

        FakeSet( Entry<String, Value>[] items, int size ) {

            this.items = items;
            this.size = size;
        }

        @Override
        public Iterator<Entry<String, Object>> iterator() {
            return new Iterator<Entry<String, Object>>() {
                int location = 0;

                @Override
                public boolean hasNext() {
                    return location < size;
                }

                @Override
                public Entry<String, Object> next() {
                    Object o = items[ location++ ];
                    return ( Entry<String, Object> ) o;
                }

                @Override
                public void remove() {

                }
            };
        }

        @Override
        public int size() {
            return size;
        }
    }
}