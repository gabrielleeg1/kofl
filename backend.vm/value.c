#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include "value.h"
#include "utils.h"

// value functions>
value_t *value_create(value_type_t type, obj_as_t obj) {
    value_t *value = malloc(sizeof(value_t));

    value->type = type;
    value->as = obj;

    return value;
}

char *value_to_str(value_t *value) {
    char *str = malloc(80 * sizeof(char));

    switch (value->type) {
        case V_TYPE_BOOL:
            sprintf(str, "%d", value->as._bool);
            break;
        case V_TYPE_DOUBLE:
            sprintf(str, "%f", value->as._double);
            break;
        case V_TYPE_INT:
            sprintf(str, "%d", value->as._int);
            break;
        case V_TYPE_OBJ:
            str = "OBJECT";
            break;
        case V_TYPE_STR:
            str = AS_CSTR(value->as._obj);
            break;
    }

    return str;
}

value_t *value_str_create(char *str) {
    string_t* string = malloc(sizeof(string_t));

    string->values = str;
    string->length = strlen(str);

    return value_create(V_TYPE_STR, (obj_as_t) {
        ._obj = (object_t *) string
    });
}

// value array functions>
value_array_t *value_array_create(int count, int capacity) {
    value_array_t *array = malloc(sizeof(value_array_t));

    array->capacity = capacity;
    array->count = count;
    array->values = malloc(capacity * sizeof(value_t));

    return array;
}

void value_array_write(value_array_t *array, value_t value) {
#ifdef VALUE_DEBUG
    printf("value_array_write(array = UNKNOWN, value = %s)\n", value_to_str(&value));
#endif

    if (array->capacity < array->count + 1) {
        size_t old_capacity = array->capacity;
        array->capacity = GROW_CAPACITY(array->capacity);
        array->values = GROW_ARRAY(value_t, array->values, old_capacity, array->capacity);
    }

    array->values[array->count] = value;
    array->count++;
}

char *value_array_dump(value_array_t *array) {
    char *str = malloc(80 * sizeof(char));

    for (size_t i = 0; i < array->capacity; ++i) {
        sprintf(str, "%s, %s", str, value_to_str(&array->values[i]));
    }

    *str += ']';

    return str;
}

void value_array_dispose(value_array_t *array) {
    free(array->values);
    free(array);
}