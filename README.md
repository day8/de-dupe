# de-dupe [![Build Status](https://magnum.travis-ci.com/Day8/cljs-structural-caching.svg?token=ZxqzShvq5GKw1TUp9DLf&branch=master)](https://magnum.travis-ci.com/Day8/cljs-structural-caching)

## Purpose

The efficient representation for serialization of (immutable persistent) data structures with a lot of structural sharing (objects that are identical and referred to in different places in the data)

## The Problem it Solves


When serialising a structure containing N references to the one sub-structure, you do NOT want that sub-structure to appear N times in the output.  That's very inefficient.  

Instead, you want such a sub-structure to appear just once. But you want to do that in a way that allows the original structure to be deserialised correctly.

## Intended use

    =>(use 'de-dupe.core :refer [create-cache])
    =>(def compressed (create-cache some-data))
    =>(= (.decompress compressed) some-data)
    true

If you want to de-dupe items that are not identical (i.e the same object reference)

    =>(use 'de-dupe.core :refer [create-eq-cache])
    =>(def compressed (create-eq-cache some-data))
    =>(= (.decompress compressed) some-data)
    true

## How it works

Its a form of dictionary compression.  The shared structures are identified and extracted from the overall data structure  (put in a dictionary) . The result is two data strcutures - the original data strcuture with all sharing removed, and a dictionary of all the shared structures. 

You can then serialise that pair in whatever way you want;  to string?  Using transit?  Etc. When it comes time to de-serialise, you provide the pair to "de-dupe" and it will return to you the original data structure.

## State of play

This Library is ok for speed at the moment but can maybe can benefit from more optimisation.

Hash seems to take a big chunk of time but if we use the ECMA 6 (Harmony) (js/Map.)
which tests by identity, the time taken does not reduce. This may be because the 
number of elements in the map increases. As this implementation uses js/Map you will need to run it on a modern browser (Chrome, IE 10, Firefox). The browser will only need to implement the Map.set() and Map.get() methods.

## Limitations

* This implementation can only cache things that can have meta-data attached to them (lists, sets, vectors, maps).
* This implementation caches everthing it can, even if the value is only used once it will be cached, which means that the decompression phase will always take longer than it should.
* This implementation by default will consider two objects as different if (identical? x y) returns false. This is to save time computing the hash of the objects to check for equality. Use de-dupe/create-eq-cache for de-duplication for non-identical structures.

## Implementation details
This implementation uses a special version of clojure.walk which keeps track of both the original form (or more correctly that returned from the inner function), and the newly modified form from the outer function.

This makes it possible in the prewalk phase (stepping down the tree from root to leaf) to cache all the forms in a js/Map (from now on referred to as the 'values' cache, this is mutable). In addition the key generated for the values cache (itself just a cljs symbol) is added to the metadata of the form. 

If there is a cache 'hit' on the values cache, in this phase the form will be replaced by the cache key that is found and the algorithm will not continue to walk down the structure.

On the way back up the tree the algorithm will begin to construct the 'compressed' cache. This is the cache where the value for each cache key itself contains cache keys. This compressed cache is constructed as the outer function will return the cache key for each value which is found by examining the metadata attached to the object on the way down.

Finally the top level compressed value is returned and assigned to the cache as cache-0.

## Testing 

This project comes with unit tests and benchmark tests on random data.

To run the unit tests with a browser

    >lein cljsbuild once test-dev

Then open resources/index_dev.html in a browser and open the javascript console to see the results

To run the benchmarks with a browser

    >lein cljsbuild once bench

Then open resources/index_bench.html in a browser and open the javascript console to see the results


## License

Copyright © 2015 Michael Thompson

Distributed under The MIT License (MIT) - See LICENSE.txt