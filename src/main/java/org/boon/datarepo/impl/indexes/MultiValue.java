package org.boon.datarepo.impl.indexes;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal class support class.
 * It tries to hide single values and nulls from the parent class.
 *
 * @param <T> Value we are holding
 */
class MultiValue<T> {
    List<T> values = null;

    public static <T> MultiValue<T> add( MultiValue<T> org, T newItem, int bucketSize ) {
        if ( org == null ) {
            return new MultiValue<>( newItem, bucketSize );
        } else {

            //This fixes Jeff's issues but introduces a performance problem.
            //I think the correct fix is to use modify not put.
//            boolean found = false;
//            for (T value : org.values) {
//                 if (value.equals ( newItem )) {
//                    found = true;
//                 }
//            }
//            if ( ! found ) {
//                org.addObject(newItem);
//            }
            org.add( newItem );
        }
        return org;
    }

    public static <T> MultiValue<T> remove( MultiValue<T> org, T removeItem ) {
        if ( org == null ) {
            return null;
        }

        if ( removeItem != null ) {
            org.remove( removeItem );
        }

        return org.size() == 0 ? null : org;
    }

    private MultiValue() {

    }

    private MultiValue( T item, int bucketSize ) {
        values = new ArrayList( bucketSize );
        values.add( item );

    }

    private void add( T item ) {

        values.add( item );
    }

    private void remove( T item ) {
        values.remove( item );
    }

    T getValue() {

        return ( values.size() > 0 ) ? values.get( 0 ) : null;
    }

    final List<T> getValues() {
        return values;
    }


    int size() {
        return values.size();
    }

    void addTo( List<T> results ) {
        results.addAll( values );
    }


}
