(ns protoproof.examples.dh
  (:refer-clojure :exclude [==])
  (:use     [clojure.core.logic.pldb]
            [clojure.core.logic :exclude [is]]
            [clojure.test])
  (:require [protoproof.core :refer :all]
            [protoproof.crypto :refer :all]
            [protoproof.transport :refer :all]
            [protoproof.intercept :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop])
)

(def knows
  (tabled [u x]
    (conde
      [(generates u x)]
      [(know-transport-mitm u x)]
      [(knowc u x)]
    )
  )
)

(def usersdb
  (db
    [user 'Alice]
    [user 'Bob]
    [user 'Eve]
    [user 'Mallory]
  )
)

(def step1
  (db
    [transport 'tr]
    [intercepter 'tr 'Mallory]
    [generates 'Alice 'a]
    [generates 'Alice 'g]
    [generates 'Bob 'g]
    [generates 'Mallory 'g]
    [generates 'Mallory 'm]
    [power 'g 'a 'ga]
    [power 'g 'm 'gm]
    [sendmsg 'tr 'Alice 'Bob 'ga]
    ;[mitm 'tr 'Mallory 'ga 'gm]
  )
)

(with-dbs [usersdb step1]
  (run* [q] (all (knows 'Mallory q) ))
)

(def usersgen (gen/elements ['Alice 'Bob 'Mallory]))
(def datagen (gen/elements ['a 'x 'y 'm]))
(def datagenexcepta (gen/elements ['x 'y 'm]))
(def powergenexcepta (gen/elements ['gx 'gy 'gm]))
(def sentmsggen (gen/elements
  (with-dbs [usersdb step1]
    (run* [q] (all (fresh [a b] (sendmsg 'tr a b q)) ))
  )
))

(gen/sample sentmsggen)
(gen/sample usersgen)
(gen/sample datagen)

(def relgen
  (gen/one-of
    [
     ;(gen/fmap (fn [[u data]] [generates u data]) (gen/tuple usersgen datagen))
     ;(gen/fmap (fn [[a b data]] [sendmsg 'tr a b data]) (gen/tuple usersgen usersgen datagen))
     (gen/fmap (fn [[data]] [generates 'Mallory data]) (gen/tuple datagenexcepta))
     (gen/fmap (fn [[data1 data2]] [power 'g data1 data2]) (gen/tuple datagenexcepta powergenexcepta))
     (gen/fmap (fn [[data]] [dropm 'tr 'Mallory data]) (gen/tuple sentmsggen))
     (gen/fmap (fn [[data1 data2]] [mitm 'tr 'Mallory data1 data2]) (gen/tuple sentmsggen (gen/one-of [datagenexcepta powergenexcepta])))
    ]
  )
)

(gen/sample (gen/vector relgen))

(def samples (last (gen/sample (gen/vector relgen))))
(println samples)
(with-dbs [usersdb step1 (apply db samples)]
  (run* [q] (all (knows 'Bob q) ))
)
(with-dbs [usersdb step1 (apply db samples)]
  (run* [q] (all (knows 'Mallory q) ))
)
(with-dbs [usersdb step1 (apply db samples)]
  (run* [q] (all (knows 'Alice q) ))
)
(set (with-dbs [usersdb step1 (apply db samples)]
  (run* [q] (all (knows 'Bob q) ))
))
(count (with-dbs [usersdb step1 (apply db samples)]
  (run* [q] (all (recv-mitm 'tr 'Bob q) ))
))

(def test-drop-db
  (prop/for-all [samples (gen/vector relgen)]
    ; if no message was dropped, Bob should know g and ga
    (= '(g ga)
      (with-dbs [usersdb step1 (apply db samples)]
        (run* [q] (all (knows 'Bob q) ))
      )
    )
))

(tc/quick-check 100 test-drop-db)


(def test-bob-knows-gm
  (prop/for-all [samples (gen/vector relgen)]
    (not
      (and
        (contains?
          (set (with-dbs [usersdb step1 (apply db samples)]
            (run* [q] (all (knows 'Bob q) ))
          )) 'gm
        )
        (not (contains?
          (set (with-dbs [usersdb step1 (apply db samples)]
            (run* [q] (all (knows 'Bob q) ))
          )) 'ga
        ))
        (= (count (with-dbs [usersdb step1 (apply db samples)]
            (run* [q] (all (recv-mitm 'tr 'Bob q) ))
          )) 1
        )
      )
    )
  )
)

(tc/quick-check 100 test-bob-knows-gm)

(def step2
  (db
    [generates 'Bob 'b]
    [power 'g 'b 'gb]
    [power 'gm 'b 'gmb]
    [power 'ga 'm 'gam]
    [sendmsg 'tr 'Bob 'Alice 'gb]
    ;[mitm 'tr 'Mallory 'gb 'gm]
  )
)

(with-dbs [usersdb step1 step2]
  (run* [q] (all (knows 'Alice q) ))
)
(with-dbs [usersdb step1 step2]
  (run* [q] (all (knows 'Bob q) ))
)
(with-dbs [usersdb step1 step2]
  (run* [q] (all (knows 'Mallory q) ))
)

(def test-mitm-gm
  (prop/for-all [samples (gen/vector relgen)]
    (not
      (and
        (=
          (set (with-dbs [usersdb step1 step2 (apply db samples)]
            (run* [q] (all (knows 'Alice q) ))
          )) #('a 'g 'ga 'gm 'gam)
        )
        (=
          (set (with-dbs [usersdb step1 step2 (apply db samples)]
            (run* [q] (all (knows 'Bob q) ))
          )) #('b 'g 'gb 'gm 'gmb)
        )
        ;(= (count (with-dbs [usersdb step1 (apply db samples)]
        ;    (run* [q] (all (recv-mitm 'tr 'Bob q) ))
        ;  )) 1
        ;)
      )
    )
  )
)

(tc/quick-check 100 test-mitm-gm)

(def step3
  (db
    [generates 'Alice 'hello]
    [sym-enc 'aes 'gam 'hello 'encryptedmsg]
    [sendmsg 'tr 'Alice 'Bob 'encryptedmsg]
    [sym-enc 'aes 'gmb 'hello 'reencryptedmsg]
    ;[mitm 'tr 'Mallory 'encryptedmsg 'reencryptedmsg]
  )
)

