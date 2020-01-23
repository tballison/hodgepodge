package org.apache.lucene;/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * A bit set that only stores longs that have at least one bit which is set.
 * The way it works is that the space of bits is divided into blocks of
 * 4096 bits, which is 64 longs. Then for each block, we have:<ul>
 * <li>a long[] which stores the non-zero longs for that block</li>
 * <li>a long so that bit <tt>i</tt> being set means that the <code>i-th</code>
 *     long of the block is non-null, and its offset in the array of longs is
 *     the number of one bits on the right of the <code>i-th</code> bit.</li></ul>
 *
 * @lucene.internal
 */
public class SparseBitSet {
  SparseFixedBitSet set = new SparseFixedBitSet(10000);
  public void set(int i) {

  }

  public boolean get(int i) {
    return true;
  }

  public int nextSet(int start) {
    return -1;
  }

}
