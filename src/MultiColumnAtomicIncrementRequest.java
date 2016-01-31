/*
 * Copyright (C) 2010-2012  The Async HBase Authors.  All rights reserved.
 * This file is part of Async HBase.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the StumbleUpon nor the names of its contributors
 *     may be used to endorse or promote products derived from this software
 *     without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.hbase.async;

import com.google.common.collect.Maps;
import org.hbase.async.generated.ClientPB.MutateRequest;
import org.hbase.async.generated.ClientPB.MutateResponse;
import org.hbase.async.generated.ClientPB.MutationProto;
import org.jboss.netty.buffer.ChannelBuffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/**
 * Atomically increments several column values in HBase.
 *
 * <h1>A note on passing {@code byte} arrays in argument</h1>
 * None of the method that receive a {@code byte[]} in argument will copy it.
 * For more info, please refer to the documentation of {@link HBaseRpc}.
 * <h1>A note on passing {@code String}s in argument</h1>
 * All strings are assumed to use the platform's default charset.
 */
public final class MultiColumnAtomicIncrementRequest extends HBaseRpc
  implements HBaseRpc.HasTable, HBaseRpc.HasKey,
             HBaseRpc.HasFamily, HBaseRpc.HasQualifier, HBaseRpc.IsEdit {

  private static byte[][] toByteArrays(String[] strArray) {
    byte[][] converted = new byte[strArray.length][];
    for(int i = 0; i < strArray.length; i++) {
      converted[i] = strArray[i].getBytes();
    }
    return converted;
  }

  private static final byte[] INCREMENT_COLUMN_VALUE = new byte[] {
    'i', 'n', 'c', 'r', 'e', 'm', 'e', 'n', 't',
    'C', 'o', 'l', 'u', 'm', 'n',
    'V', 'a', 'l', 'u', 'e'
  };

  private final byte[] family;
  private final byte[][] qualifiers;
  private long[] amounts;
  private boolean durable = true;

  /**
   * Constructor.
   * <strong>These byte arrays will NOT be copied.</strong>
   * @param table The non-empty name of the table to use.
   * @param key The row key of the value to increment.
   * @param family The column family of the value to increment.
   * @param qualifier The column qualifier of the value to increment.
   * @param amount Amount by which to increment the value in HBase.
   * If negative, the value in HBase will be decremented.
   */
  public MultiColumnAtomicIncrementRequest(final byte[] table,
                                           final byte[] key,
                                           final byte[] family,
                                           final byte[][] qualifiers,
                                           final long[] amounts) {
    super(table, key);
    KeyValue.checkFamily(family);
    for (byte[] qualifier : qualifiers) {
      KeyValue.checkQualifier(qualifier);
    }
    this.family = family;

    assert(qualifiers.length == amounts.length);
    this.qualifiers = qualifiers;
    if (amounts != null) {
      this.amounts = amounts;
    } else {
      this.amounts = new long[qualifiers.length];
      Arrays.fill(this.amounts, 1L);
    }
  }

  /**
   * Constructor.  This is equivalent to:
   * {@link #AtomicIncrementRequest(byte[], byte[], byte[], byte[], long)
   * AtomicIncrementRequest}{@code (table, key, family, qualifier, 1)}
   * <p>
   * <strong>These byte arrays will NOT be copied.</strong>
   * @param table The non-empty name of the table to use.
   * @param key The row key of the value to increment.
   * @param family The column family of the value to increment.
   * @param qualifier The column qualifier of the value to increment.
   */
  public MultiColumnAtomicIncrementRequest(final byte[] table,
                                           final byte[] key,
                                           final byte[] family,
                                           final byte[][] qualifiers) {
    this(table, key, family, qualifiers, null);
  }

  /**
   * Constructor.
   * All strings are assumed to use the platform's default charset.
   * @param table The non-empty name of the table to use.
   * @param key The row key of the value to increment.
   * @param family The column family of the value to increment.
   * @param qualifier The column qualifier of the value to increment.
   * @param amount Amount by which to increment the value in HBase.
   * If negative, the value in HBase will be decremented.
   */
  public MultiColumnAtomicIncrementRequest(final String table,
                                           final String key,
                                           final String family,
                                           final String[] qualifiers,
                                           final long[] amounts) {
    this(table.getBytes(), key.getBytes(), family.getBytes(),
        toByteArrays(qualifiers), amounts);
  }

  /**
   * Constructor.  This is equivalent to:
   * All strings are assumed to use the platform's default charset.
   * {@link #AtomicIncrementRequest(String, String, String, String, long)
   * AtomicIncrementRequest}{@code (table, key, family, qualifier, 1)}
   * @param table The non-empty name of the table to use.
   * @param key The row key of the value to increment.
   * @param family The column family of the value to increment.
   * @param qualifier The column qualifier of the value to increment.
   */
  public MultiColumnAtomicIncrementRequest(final String table,
                                           final String key,
                                           final String family,
                                           final String[] qualifiers) {
    this(table.getBytes(), key.getBytes(), family.getBytes(),
         toByteArrays(qualifiers), null);
  }

  /**
   * Returns the amount by which the value is going to be incremented.
   */
  public long[] getAmounts() {
    return amounts;
  }

  /**
   * Changes the amounts by which the values are going to be incremented.
   * @param amounts The new amounts.  If negative, the value will be decremented.
   */
  public void setAmounts(final long[] amounts) {
    this.amounts = amounts;
  }

  @Override
  byte[] method(final byte server_version) {
    return (server_version >= RegionClient.SERVER_VERSION_095_OR_ABOVE
            ? MUTATE
            : INCREMENT_COLUMN_VALUE);
  }

  @Override
  public byte[] table() {
    return table;
  }

  @Override
  public byte[] key() {
    return key;
  }

  @Override
  public byte[] family() {
    return family;
  }

  @Override
  public byte[] qualifier() {
    throw new UnsupportedOperationException("qualifier() is not supported by " + this.getClass().getName());
  }

  public byte[][] qualifiers() {
    return qualifiers;
  }

  public String toString() {
    return super.toStringWithQualifier("AtomicIncrementRequest",
                                       family,
                                       new byte[] { 'N', 'A' },
                                       ", qualifies=" + qualifiers + ", amount=" + amounts);
  }

  // ---------------------- //
  // Package private stuff. //
  // ---------------------- //

  /**
   * Changes whether or not this atomic increment should use the WAL.
   * @param durable {@code true} to use the WAL, {@code false} otherwise.
   */
  void setDurable(final boolean durable) {
    this.durable = durable;
  }

  private int predictSerializedSize() {
    int size = 0;
    size += 4;  // int:  Number of parameters.
    size += 1;  // byte: Type of the 1st parameter.
    size += 3;  // vint: region name length (3 bytes => max length = 32768).
    size += region.name().length;  // The region name.
    size += 1;  // byte: Type of the 2nd parameter.
    size += 3;  // vint: row key length (3 bytes => max length = 32768).
    size += key.length;  // The row key.
    size += 1;  // byte: Type of the 3rd parameter.
    size += 1;  // vint: Family length (guaranteed on 1 byte).
    size += family.length;  // The family.
    size += 1;  // byte: Type of the 4th parameter.
    size += 3;  // vint: Qualifier length.
    for(byte[] qualifier : qualifiers) {
      size += qualifier.length;  // The qualifier.
    }
    size += 1;  // byte: Type of the 5th parameter.
    size += 8 * amounts.length;  // long: Amount.
    size += 1;  // byte: Type of the 6th parameter.
    size += 1;  // bool: Whether or not to write to the WAL.
    return size;
  }

  /** Serializes this request.  */
  ChannelBuffer serialize(final byte server_version) {
    if (server_version < RegionClient.SERVER_VERSION_095_OR_ABOVE) {
      throw new UnsupportedOperationException(server_version + " is not supported by " + this.getClass().getName());
    }
    MutationProto.Builder incr = MutationProto.newBuilder()
      .setRow(Bytes.wrap(key))
      .setMutateType(MutationProto.MutationType.INCREMENT);

    for (int i = 0; i < qualifiers.length; i++) {
      final MutationProto.ColumnValue.QualifierValue qualifier =
        MutationProto.ColumnValue.QualifierValue.newBuilder()
        .setQualifier(Bytes.wrap(this.qualifiers[i]))
        .setValue(Bytes.wrap(Bytes.fromLong(this.amounts[i])))
        .build();
      final MutationProto.ColumnValue column =
        MutationProto.ColumnValue.newBuilder()
        .setFamily(Bytes.wrap(family))
        .addQualifierValue(qualifier)
        .build();
      incr.addColumnValue(column);
    }

    if (!durable) {
      incr.setDurability(MutationProto.Durability.SKIP_WAL);
    }
    final MutateRequest req = MutateRequest.newBuilder()
      .setRegion(region.toProtobuf())
      .setMutation(incr.build())
      .build();
    return toChannelBuffer(MUTATE, req);
  }


  @Override
  Object deserialize(final ChannelBuffer buf, int cell_size) {
    final MutateResponse resp = readProtobuf(buf, MutateResponse.PARSER);
    // An increment must always produce a result, so we shouldn't need to
    // check whether the `result' field is set here.
    final ArrayList<KeyValue> kvs = GetRequest.convertResult(resp.getResult(),
                                                             buf, cell_size);

    Map<byte[], Long> updatedValues = Maps.newHashMap();
    for (KeyValue kv : kvs) {
      updatedValues.put(kv.qualifier(), Bytes.getLong(kv.value()));
    }
    return updatedValues;
  }

}
