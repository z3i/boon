package org.boon.json.implementation;

import org.boon.core.Typ;
import org.boon.core.Type;
import org.boon.core.Value;
import org.boon.core.reflection.MapObjectConversion;
import org.boon.core.reflection.fields.FieldAccessMode;
import org.boon.core.reflection.fields.FieldsAccessor;
import org.boon.core.value.*;
import org.boon.json.JsonException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * This works in index overlay mode or chop mode.
 * Chop mode reduces possibility of memory leak but causes a few more buffer copies as it chops up the buffer.
 */
public class JsonFastParser extends JsonParserCharArray {


    private static ValueBase EMPTY_LIST = new ValueBase ( Collections.EMPTY_LIST );


    private final boolean useValues;
    private final boolean chop;
    private final boolean lazyChop;

    public JsonFastParser(  ) {
        this( FieldAccessMode.create( FieldAccessMode.FIELD ) );
    }

    public JsonFastParser( FieldAccessMode mode ) {
        this( FieldAccessMode.create(mode) );
    }


    public JsonFastParser(FieldsAccessor fieldsAccessor) {
        super( fieldsAccessor );
        useValues = false;
        chop = false;
        lazyChop = true;

    }


    public JsonFastParser( FieldsAccessor fieldsAccessor, boolean useValues ) {
        super( fieldsAccessor );

        this.useValues = useValues;
        chop = false;
        lazyChop = true;
    }


    public JsonFastParser( FieldsAccessor fieldsAccessor, boolean useValues, boolean chop ) {
        super( fieldsAccessor );

        this.useValues = useValues;
        this.chop = chop;
        lazyChop = !chop;

    }


    public JsonFastParser( FieldsAccessor fieldsAccessor, boolean useValues, boolean chop, boolean lazyChop ) {
        super( fieldsAccessor );
        this.useValues = useValues;
        this.chop = chop;
        this.lazyChop = lazyChop;
    }


    protected final Value decodeJsonObjectLazyFinalParse() {

        char[] array = charArray;

        if ( __currentChar == '{' )
            __index++;

        ValueMap map =  useValues ? new ValueMapImpl () : new LazyValueMap ( lazyChop );
        Value value  = new ValueBase ( map );

        objectLoop:
        for (; __index < array.length; __index++ ) {
            skipWhiteSpace ();
            switch ( __currentChar ) {

                case '"':
                    Value key = decodeStringOverlay ();
                    skipWhiteSpace ();

                    if ( __currentChar != ':' ) {

                        complain ( "expecting current character to be " + charDescription ( __currentChar ) + "\n" );
                    }
                    __index++;

                    Value item = decodeValueOverlay ();

                    skipWhiteSpace ();


                    MapItemValue miv = new MapItemValue ( key, item );

                    map.add ( miv );
            }

            switch ( __currentChar ) {
                case '}':
                    __index++;
                    break objectLoop;

                case ',':
                    continue;

                default:

                    complain (
                            "expecting '}' or ',' but got current char " + charDescription ( __currentChar ) );

            }
        }
        return value;
    }

    protected Value decodeValue() {
        return decodeValueOverlay ();
    }

    private Value decodeValueOverlay() {


        skipWhiteSpace ();

        switch ( __currentChar ) {


            case '"':
                return decodeStringOverlay ();

            case '{':
                return decodeJsonObjectLazyFinalParse ();

            case 't':
                return decodeTrue () == true ? ValueBase.TRUE : ValueBase.FALSE;

            case 'f':
                return decodeFalse () == false ? ValueBase.FALSE : ValueBase.TRUE;

            case 'n':
                return decodeNull () == null ? ValueBase.NULL : ValueBase.NULL;

            case '[':
                return decodeJsonArrayOverlay ();


            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
            case '0':
                return decodeNumberOverlay (false);

            case '-':
                return decodeNumberOverlay (true);

            default:

                throw new JsonException ( exceptionDetails ( "Unable to determine the " +
                        "current character, it is not a string, number, array, or object" ) );


        }

    }


    private final Value decodeNumberOverlay(final boolean minus) {


        char[] array = charArray;

        final int startIndex = __index;
        int index =  __index;
        char currentChar;
        boolean doubleFloat = false;

        if (minus && index + 1 < array.length) {
            index++;
        }



        while (true) {
            currentChar = array[index];
            if ( isNumberDigit ( currentChar )) {
                //noop
            } else if ( currentChar <= 32 ) { //white
                break;
            } else if ( isDelimiter ( currentChar ) ) {
                break;
            } else if ( isDecimalChar (currentChar) ) {
                doubleFloat = true;
            }
            index++;
            if (index   >= array.length) break;
        }

        __index = index;
        __currentChar = currentChar;

        Type type = doubleFloat ? Type.DOUBLE : Type.INTEGER;

        ValueInCharBuf value = new ValueInCharBuf ( chop, type, startIndex, __index, this.charArray );

        return value;


    }




    private Value decodeStringOverlay() {

        char[] array = charArray;
        int index = __index;
        char currentChar = charArray[index];

        if ( index < array.length && currentChar == '"' ) {
            index++;
        }

        final int startIndex = index;


        boolean encoded = hasEscapeChar ( array, index, indexHolder );
        index = indexHolder[0];


        if (encoded)  {
                index = findEndQuote ( array, index );
        }

        Value value = new ValueInCharBuf ( chop, Type.STRING, startIndex, index, array, encoded, true );


        if ( index < array.length ) {
            index++;
        }

        __index = index;
        return value;
    }



    private Value decodeJsonArrayOverlay() {

        char [] array = charArray;
        if ( __currentChar == '[' ) {
            this.nextChar ();
        }

        skipWhiteSpace ();




        /* the list might be empty  */
        if ( __currentChar == ']' ) {
            this.nextChar ();
            return EMPTY_LIST;
        }


        List<Object> list;

        if ( useValues ) {
            list = new ArrayList<> ();
        } else {
            list = new ValueList ( lazyChop );
        }

        Value value = new ValueBase ( list );


        Value item;

        arrayLoop:
        for ( ;__index < array.length; __index++) {
            item = decodeValueOverlay ();

            list.add ( item );


            skipWhiteSpace ();

            switch (__currentChar) {
                case ',':
                    continue;
                case ']':
                    __index++;
                    break arrayLoop;
                default:
                    complain (
                        String.format ( "expecting a ',' or a ']', " +
                                " but got \nthe current character of  %s " +
                                " on array size of %s \n", charDescription ( __currentChar ), list.size () )
                );

            }
        }
        return value;
    }


    protected final <T> T convert( Class<T> type, Object object ) {
        if ( type==Object.class || type == Map.class || type == List.class ) {
            return (T)object;
        } else {
            if ( object instanceof Map ) {
                return MapObjectConversion.fromValueMap( fieldsAccessor,  ( Map<String, org.boon.core.Value> ) object, type );
            } else if ( object instanceof Value &&  Typ.isBasicType ( type )  ) {
                return (T)( (Value) object).toValue ();
            }
            else {
                return (T)object;
            }
        }
    }



    protected final Object decodeFromChars( char[] cs ) {
        Value value =  ( ( Value ) super.decodeFromChars ( cs ) );
        if (value.isContainer ()) {
            return value.toValue ();
        } else {
            return value;
        }
    }


}