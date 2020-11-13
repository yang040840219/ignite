/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.schema.marshaller;

import java.lang.reflect.Field;
import java.util.BitSet;
import java.util.UUID;
import org.apache.ignite.internal.schema.ByteBufferTuple;
import org.apache.ignite.internal.schema.Column;
import org.apache.ignite.internal.schema.Columns;
import org.apache.ignite.internal.schema.NativeType;
import org.apache.ignite.internal.schema.SchemaDescriptor;
import org.apache.ignite.internal.schema.Tuple;
import org.apache.ignite.internal.schema.TupleAssembler;
import org.jetbrains.annotations.NotNull;

/**
 * Cache objects (de)serializer.
 * <p>
 * TODO: Extract interface.
 */
public class JavaSerializer {
    /**
     * Gets binary read/write mode for given class.
     *
     * @param cls Type.
     * @return Binary mode.
     */
    public static BinaryMode mode(Class<?> cls) {
        assert cls != null;

        // Primitives.
        if (cls == byte.class)
            return BinaryMode.P_BYTE;
        else if (cls == short.class)
            return BinaryMode.P_SHORT;
        else if (cls == int.class)
            return BinaryMode.P_INT;
        else if (cls == long.class)
            return BinaryMode.P_LONG;
        else if (cls == float.class)
            return BinaryMode.P_FLOAT;
        else if (cls == double.class)
            return BinaryMode.P_DOUBLE;

            // Boxed primitives.
        else if (cls == Byte.class)
            return BinaryMode.BYTE;
        else if (cls == Short.class)
            return BinaryMode.SHORT;
        else if (cls == Integer.class)
            return BinaryMode.INT;
        else if (cls == Long.class)
            return BinaryMode.LONG;
        else if (cls == Float.class)
            return BinaryMode.FLOAT;
        else if (cls == Double.class)
            return BinaryMode.DOUBLE;

            // Other types
        else if (cls == byte[].class)
            return BinaryMode.BYTE_ARR;
        else if (cls == String.class)
            return BinaryMode.STRING;
        else if (cls == UUID.class)
            return BinaryMode.UUID;
        else if (cls == BitSet.class)
            return BinaryMode.BITSET;

        return null;
    }

    /**
     * Reads value object from tuple.
     *
     * @param reader Reader.
     * @param colIdx Column index.
     * @param mode Binary read mode.
     * @return Read value object.
     */
    static Object readRefValue(Tuple reader, int colIdx, BinaryMode mode) {
        assert reader != null;
        assert colIdx >= 0;

        Object val = null;

        switch (mode) {
            case BYTE:
                val = reader.byteValueBoxed(colIdx);

                break;

            case SHORT:
                val = reader.shortValueBoxed(colIdx);

                break;

            case INT:
                val = reader.intValueBoxed(colIdx);

                break;

            case LONG:
                val = reader.longValueBoxed(colIdx);

                break;

            case FLOAT:
                val = reader.floatValueBoxed(colIdx);

                break;

            case DOUBLE:
                val = reader.doubleValueBoxed(colIdx);

                break;

            case STRING:
                val = reader.stringValue(colIdx);

                break;

            case UUID:
                val = reader.uuidValue(colIdx);

                break;

            case BYTE_ARR:
                val = reader.bytesValue(colIdx);

                break;

            case BITSET:
                val = reader.bitmaskValue(colIdx);

                break;

            default:
                assert false : "Invalid mode: " + mode;
        }

        return val;
    }

    /**
     * Writes reference value to tuple.
     *
     * @param val Value object.
     * @param writer Writer.
     * @param mode Write binary mode.
     */
    static void writeRefObject(Object val, TupleAssembler writer, BinaryMode mode) {
        assert writer != null;

        if (val == null) {
            writer.appendNull();

            return;
        }

        switch (mode) {
            case BYTE:
                writer.appendByte((Byte)val);

                break;

            case SHORT:
                writer.appendShort((Short)val);

                break;

            case INT:
                writer.appendInt((Integer)val);

                break;

            case LONG:
                writer.appendLong((Long)val);

                break;

            case FLOAT:
                writer.appendFloat((Float)val);

                break;

            case DOUBLE:
                writer.appendDouble((Double)val);

                break;

            case STRING:
                writer.appendString((String)val);

                break;

            case UUID:
                writer.appendUuid((UUID)val);

                break;

            case BYTE_ARR:
                writer.appendBytes((byte[])val);

                break;

            case BITSET:
                writer.appendBitmask((BitSet)val);

                break;

            default:
                assert false : "Invalid mode: " + mode;
        }
    }

    /** Schema. */
    private final SchemaDescriptor schema;

    /** Key class. */
    private final Class<?> keyClass;

    /** Value class. */
    private final Class<?> valClass;

    /** Key marshaller. */
    private final Marshaller keyMarsh;

    /** Value marshaller. */
    private final Marshaller valMarsh;

    /**
     * Constructor.
     *
     * @param schema Schema.
     * @param keyClass Key type.
     * @param valClass Value type.
     */
    public JavaSerializer(SchemaDescriptor schema, Class<?> keyClass, Class<?> valClass) {
        this.schema = schema;
        this.keyClass = keyClass;
        this.valClass = valClass;

        keyMarsh = createMarshaller(schema.keyColumns(), 0, keyClass);
        valMarsh = createMarshaller(schema.valueColumns(), schema.keyColumns().length(), valClass);
    }

    /**
     * Creates marshaller for class.
     *
     * @param cols Columns.
     * @param firstColId First column position in schema.
     * @param aClass Type.
     * @return Marshaller.
     */
    @NotNull private static Marshaller createMarshaller(Columns cols, int firstColId, Class<?> aClass) {
        final BinaryMode mode = mode(aClass);

        if (mode != null) {
            final Column col = cols.column(0);

            assert cols.length() == 1;
            assert mode.typeSpec() == col.type().spec() : "Target type is not compatible.";
            assert !aClass.isPrimitive() : "Non-nullable types are not allowed.";

            return new Marshaller(FieldAccessor.createIdentityAccessor(col, firstColId, mode));
        }

        FieldAccessor[] fieldAccessors = new FieldAccessor[cols.length()];

        // Build accessors
        for (int i = 0; i < cols.length(); i++) {
            final Column col = cols.column(i);

            final int colIdx = firstColId + i; /* Absolute column idx in schema. */
            fieldAccessors[i] = FieldAccessor.create(aClass, col, colIdx);
        }

        return new Marshaller(ObjectFactory.classFactory(aClass), fieldAccessors);
    }

    /**
     * Writes key-value pair to tuple.
     *
     * @param key Key object.
     * @param val Value object.
     * @return Serialized key-value pair.
     */
    public byte[] serialize(Object key, Object val) throws SerializationException {
        assert keyClass.isInstance(key);
        assert val == null || valClass.isInstance(val);

        final TupleAssembler asm = createAssembler(key, val);

        keyMarsh.writeObject(key, asm);

        if (val != null)
            valMarsh.writeObject(val, asm);
        else
            assert false; // TODO: add tomstone support and remove assertion.

        return asm.build();
    }

    /**
     * Creates TupleAssebler for key-value pair.
     *
     * @param key Key object.
     * @param val Value object.
     * @return Tuple assembler.
     * @throws SerializationException If failed.
     */
    @NotNull private TupleAssembler createAssembler(Object key, Object val) throws SerializationException {
        ObjectStatistic keyStat = collectObjectStats(schema.keyColumns(), keyMarsh, key);
        ObjectStatistic valStat = collectObjectStats(schema.valueColumns(), valMarsh, val);

        int size = TupleAssembler.tupleSize(
            schema.keyColumns(), keyStat.nonNullFields, keyStat.nonNullFieldsSize,
            schema.valueColumns(), valStat.nonNullFields, valStat.nonNullFieldsSize);

        return new TupleAssembler(schema, size, keyStat.nonNullFields, valStat.nonNullFields);
    }

    /**
     * Object statistic.
     */
    private static class ObjectStatistic {
        /** Non-null fields of varlen type. */
        int nonNullFields;

        /** Length of all non-null fields of varlen types. */
        int nonNullFieldsSize;

        /** Constructor. */
        public ObjectStatistic(int nonNullFields, int nonNullFieldsSize) {
            this.nonNullFields = nonNullFields;
            this.nonNullFieldsSize = nonNullFieldsSize;
        }
    }

    /**
     * Reads object fields and gather statistic.
     *
     * @param cols Schema columns.
     * @param marsh Marshaller.
     * @param obj Object.
     * @return Object statistic.
     * @throws SerializationException If failed.
     */
    private ObjectStatistic collectObjectStats(Columns cols, Marshaller marsh, Object obj)
        throws SerializationException {
        if (obj == null || cols.firstVarlengthColumn() < 0 /* No varlen columns */)
            return new ObjectStatistic(0, 0);

        int cnt = 0;
        int size = 0;

        for (int i = cols.firstVarlengthColumn(); i < cols.length(); i++) {
            final Object val = marsh.value(obj, i);

            if (val == null || cols.column(i).type().spec().fixedLength())
                continue;

            size += getValueSize(val, cols.column(i).type());
            cnt++;
        }

        return new ObjectStatistic(cnt, size);
    }

    /**
     * Calculates size for serialized value of varlen type.
     *
     * @param val Field value.
     * @param type Mapped type.
     * @return Serialized value size.
     */
    private int getValueSize(Object val, NativeType type) {
        switch (type.spec()) {
            case BYTES:
                return ((byte[])val).length;

            case STRING:
                return TupleAssembler.utf8EncodedLength((CharSequence)val);

            default:
                throw new IllegalStateException("Unsupported test varsize type: " + type);
        }
    }

    /**
     * @return Key object.
     */
    public Object deserializeKey(byte[] data) throws SerializationException {
        final Tuple tuple = new ByteBufferTuple(schema, data);

        final Object o = keyMarsh.readObject(tuple);

        assert keyClass.isInstance(o);

        return o;
    }

    /**
     * @return Value object.
     */
    public Object deserializeValue(byte[] data) throws SerializationException {
        final Tuple tuple = new ByteBufferTuple(schema, data);

        // TODO: add tomstone support.

        final Object o = valMarsh.readObject(tuple);

        assert valClass.isInstance(o);

        return o;
    }
}
