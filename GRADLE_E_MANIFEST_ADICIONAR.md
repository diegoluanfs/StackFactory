# Ajustes obrigatórios nos arquivos existentes

## app/build.gradle.kts — DENTRO do bloco dependencies (não substitua o arquivo inteiro)

```kotlin
implementation("com.google.android.gms:play-services-ads:25.5.0")
implementation("com.google.android.ump:user-messaging-platform:4.0.0")
```

Se usa version catalog, registre as dependências no catalog e referencie seus aliases.

## app/src/main/AndroidManifest.xml

Dentro de `<application>` (sem remover suas activities/workers):

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-3940256099942544~3347511713" />
```

Se ainda não tiver, fora do `<application>`, dentro de `<manifest>`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

Não remova `POST_NOTIFICATIONS` existente. `AD_ID` é declarado pelo SDK atual via manifest merger; confira o manifesto final e declaração no Play Console.

## Publicação

- IDs incluídos são OFICIAIS DE TESTE. Nunca lance com eles; substitua APP_ID no manifest e IDs em ads/AdConfig.kt pelos seus.
- Configure as mensagens e opções de privacidade no AdMob (Privacy & messaging).
- Atualize Política de privacidade, seção Segurança dos dados e declaração de ID de publicidade no Play Console.
- Use dispositivos de teste durante a validação de anúncios próprios.
- O UMP já mostra formulário quando aplicável, e a tela inicial disponibiliza opções de privacidade quando exigidas.

## Testar

```powershell
.\gradlew.bat clean :app:assembleDebug
```

Teste sequência de derrotas e transição após terceira, e comportamento offline (jogo não pode travar).
