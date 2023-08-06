module dorkbox.config {
    exports dorkbox.config;

    requires transitive dorkbox.json;
    requires transitive dorkbox.os;
    requires transitive dorkbox.updates;

    requires transitive kotlin.reflect;
    requires transitive kotlin.stdlib;
}
