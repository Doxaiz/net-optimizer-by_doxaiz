# Сборка и проверки

## Окружение

- JDK 17, Gradle 8.7 и Android SDK Platform 34.
- Минимальная версия устройства — Android 12 (API 31), target SDK — 34.
- Kotlin 2.0.0, Jetpack Compose и Room. Gradle wrapper JAR и скрипты в репозитории не поставляются; команды ниже используют установленный Gradle.

```bash
gradle --no-daemon testReleaseUnitTest
gradle --no-daemon lintRelease
```

Для подписанной release-сборки настройте вне репозитория переменные `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, затем выполните `gradle --no-daemon assembleRelease`. Не вставляйте фактические значения в Git, сообщения или логи.

Workflow **Build APK** при push и pull request запускает тесты и собирает debug APK как артефакт для разработки. Debug-подпись отличается от release: такой APK не предназначен для публикации и обычно не обновит установленный release. Подписанная сборка запускается вручную через `workflow_dispatch` и ожидает repository secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` и `ANDROID_KEY_PASSWORD`; при их отсутствии она останавливается. Для проверки конфигурации есть отдельный workflow с одноразовым тестовым ключом: такой APK **не предназначен для публикации**. Workflows тестирования не публикуют релиз автоматически.

## Безопасность исходников

```bash
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check-sensitive-files.py
```

Локальный скрипт и CI проверяют ряд известных форматов токенов, секретных файлов и явных паролей подписи **только в текущем дереве**. Это базовая проверка, а не полное сканирование секретов, истории, внешних сервисов или артефактов.

Ранее ключ подписи и пароль были опубликованы в истории; удаление файлов из текущей ветки и `.gitignore` не делает их секретными. Информация об известных рисках и проверках — в [AUDIT.md](../AUDIT.md).

## Проверка установки

На эмуляторах Android 12 и 14 проверены установка и обновление выпущенного APK, первый запуск и основные вкладки. Подробности и ссылки на конкретные CI-запуски — в [AUDIT.md](../AUDIT.md). Скрипты `scripts/audit-release-emulator.py` и `scripts/check-usability-emulator.py` удаляют приложение и его данные: **запускайте их только на одноразовом эмуляторе**, никогда на личном устройстве.

Имя версии и внутренний `versionCode` — разные вещи: для обновления Android требует увеличивать `versionCode`. Проверки на эмуляторе не заменяют проверку на реальном телефоне, в сети оператора и с выбранной версией Shizuku.
