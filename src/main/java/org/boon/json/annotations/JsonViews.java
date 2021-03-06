package org.boon.json.annotations;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * @author Rick Hightower
 *
 */
@Target ({ ElementType.METHOD, ElementType.FIELD })
@Retention ( RetentionPolicy.RUNTIME)
public @interface JsonViews {

    String[] includeWithViews();
    String[] ignoreWithViews();
}
