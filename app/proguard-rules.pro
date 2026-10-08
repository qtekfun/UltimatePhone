# Project-specific R8 rules. Libraries in use ship their own consumer rules.
#
# Checked against the release mapping (docs/PERFORMANCE.md): Room's generated database classes, the WorkManager workers,
# the Hilt entry points, the services and the kotlinx.serialization serializers are all kept by the consumer rules of
# their libraries, so nothing is added here. libphonenumber needs no rule: it loads its metadata as Java resources
# (com/google/i18n/phonenumbers/data/*), which R8 does not touch, and the built-in prefix rules are a Java resource too.
# Bouncy Castle needs none either: only the Ed25519 signer and Argon2 are referenced, so R8 keeps about 20 of its classes.
