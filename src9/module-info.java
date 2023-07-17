module dorkbox.config {
    exports dorkbox.config;

    requires transitive dorkbox.updates;
    requires transitive dorkbox.os;

    requires transitive kotlin.reflect;
    requires transitive kotlin.logging.jvm;
    requires transitive kotlin.stdlib;

    requires transitive com.squareup.moshi;
}
