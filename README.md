# de-dupe

# The Problem

Persistent Data Structures use structural sharing to create an efficient in-memory representation, but these same structures can be a problem to serialize/deserialize. 

**First**, the shared parts are duplicated in the serialised output which can lead to a large and inefficient representation.

**Second**, when the process is reversed,  deserialization doesn't recreate the original, shared, effcient in-memory representation because sharing information was lost in the original serialisation.

## The Solution Provided

This library "de-duplicates" persistent datastructures so they can be more efficiently serialised.

It provides `de-dupe`, `de-dupe-eq`, and `expand`.

`de-dupe` takes a persistent data structure, `pds`, and it returns a hash-map, which maps "tokens" (ids) to duplicated sub-nodes. The item with token `de-dupe.cache/cache-0` represents the root of `pds`.

Having used `de-dupe`, you are expected to then serialise the hash-map using whatever method makes sense to your use case - perhaps [transit](https://github.com/cognitect/transit-cljs), or serialization with `edn`. So `de-dupe` is a pre-processor for use before transit.

Later, `expand` can be used to reverse the process - you give it a hash-map and it reforms the original, sharing and all. 

## Usage

Add this dependency to your project.clj
```
[de-dupe "0.2.2"]
```

Then add this requirement to your cljs file:
```
(:require [de-dupe.core :refer [de-dupe de-dupe-eq expand]])
```

The default behaviour is to only recognize duplicates when they compare
with `identical?`

```
(def compressed (de-dupe some-data))
;  if you now compare 
;  (count (prn-str compressed)) and (count (prn-str some-data))
;  you will see the degree of compression

;  to recover your original data
(def some-data2 (expand compressed))
```

If you want to de-dupe items that are not identical (i.e the same object reference)
```
(def compressed (de-dupe-eq some-data))
;  if you now compare 
;  (count (prn-str compressed)) and (count (prn-str some-data))
;  you will see the degree of compression; it will be greater than de-dupe

;  to recover your original data
(def some-data2 (expand compressed))
```

## State of play

The implementation chooses smaller serialized output and faster expansion over the fastest possible compression. It first identifies repeated structures so unique child values can stay inline, and expansion memoizes cache entries so each one is rebuilt once. The consequence of these tradeoffs is much smaller output and much faster `expand`, but slower compression. Run `npm run bench` to see the current size ratios and timings.

As this implementation uses `js/Map`, you will need to run it on a JavaScript runtime which implements `Map.has`, `Map.get`, and `Map.set`.

## Limitations

* This implementation can only cache things that can have metadata attached to them during compression (lists, sets, vectors, maps).
* This implementation only extracts cacheable values that repeat. Unique values stay inline.
* This implementation by default will consider two objects as different if `(identical? x y)` returns false. This is to save time computing hashes of objects to check for equality. Use `de-dupe-eq` for de-duplication of equal but non-identical structures.

## Implementation details

Its a form of dictionary compression. The shared structures are identified and extracted from the overall data structure, and then added to the result hash-map. 
The result hash-map also contains a represenation of the root note of the `pds`.

This implementation uses a special version of clojure.walk which keeps track of both the original form (or more correctly that returned from the inner function), and the newly modified form from the outer function.

This makes it possible in the prewalk phase (stepping down the tree from root to leaf) to cache all the forms in a `js/Map` (from now on referred to as the values cache, this is mutable). In addition, the generated cache key is added to the metadata of the form while compression is running.

If there is a cache 'hit' on the values cache, in this phase the form will be replaced by the cache key that is found and the algorithm will not continue to walk down the structure.

On the way back up the tree the algorithm will begin to construct the 'compressed' cache. This is the cache where the value for each cache key itself contains cache keys. This compressed cache is constructed as the outer function will return the cache key for each value which is found by examining the metadata attached to the object on the way down.

Finally the top level compressed value is returned and assigned to the cache as `de-dupe.cache/cache-0`. Cache keys are namespaced symbols so they survive normal printed serialization.

## Testing 

This project comes with unit tests and benchmark tests on random data.

To run the unit tests non-interactively with Node:

    >clojure -M:test

or:

    >npm test

Leiningen users can run the same test path with:

    >lein test

To run the benchmark with Node:

    >npm run bench


## License

Copyright © 2015-2026 Michael Thompson

Distributed under The MIT License (MIT) - See LICENSE.txt
