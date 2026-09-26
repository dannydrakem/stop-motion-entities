# Stepped Player Animations

Клиентский Fabric-мод для дискретизации только визуальной позы игрока. Проект развивается небольшими проверяемыми этапами.

## Текущий этап

Этап 3, тестовый кандидат: узкий слой совместимости EMF + Emotecraft. Он не позволяет двум встроенным защитным механизмам EMF полностью останавливать анимации FA или заменять модель игрока на ванильную во время активного PlayerAnimator.

Compatibility-Mixin применяется только при одновременном наличии целевых модов. Без EMF и Emotecraft мод загружается в пассивном режиме.

Зафиксированные версии:

- Minecraft 1.21.1
- Java 21
- Fabric Loader 0.17.2
- Fabric API 0.116.5+1.21.1
- Fabric Loom 1.11.1

Проверенный тестовый набор этапа 3:

- Entity Model Features 3.3.9
- Entity Texture Features 7.2.4
- Emotecraft 2.4.12
- PlayerAnimator 2.0.4+1.21.1
- Fresh Animations 1.10.4
- FA Player Extension 1.1

## Сборка

Требуется JDK 21 (`JAVA_HOME` должен указывать на него).

На Windows:

```text
gradlew.bat build
```

Готовый jar появляется в `build/libs/`.
