package dependency

class EmptyExtension {
    // Ignoring all method calls
    def methodMissing(String name, def args) {}
}
