package com.emberr.domain.selfhost.crypto

class IncorrectPassphraseException(cause: Throwable) :
    Exception("Incorrect passphrase, could not unlock the existing vault", cause)

class DamagedVaultFileException(cause: Throwable) :
    Exception("The vault file on the server is damaged and cannot be opened", cause)
