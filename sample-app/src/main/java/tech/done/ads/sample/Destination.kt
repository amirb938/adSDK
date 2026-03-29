package tech.done.ads.sample

sealed class Destination {
    data object MainMenu : Destination()
    data object SimpleVast : Destination()
    data object SimpleVmap : Destination()
    data object CustomUiVmap : Destination()
    data object SimidVmap : Destination()
    data object SimidVmapNoSkip : Destination()
}

