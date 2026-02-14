import os
import re
import asyncio

from aiogram import Bot, Dispatcher, F, Router
from aiogram.types import Message, InlineKeyboardButton, InlineKeyboardMarkup, CallbackQuery
from aiogram.enums import ParseMode
from aiogram.client.session.aiohttp import AiohttpSession
from aiogram.filters import Command, CommandStart

BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN")

# ID вашей группы (формат может быть отрицательный, типа -1001234567890)
GROUP_ID = -1001234567890

# список ID админов
ADMINS = {123456789, 987654321}

# глобально храним последний курс
last_rate: float | None = None

# состояние ожидания ввода нового курса от админа
waiting_for_new_rate: set[int] = set()

# обычный роутер для логики "курс"
main_router = Router()
# роутер для админ‑панели
admin_router = Router()


def get_admin_keyboard() -> InlineKeyboardMarkup:
    kb = [
        [
            InlineKeyboardButton(text="📈 Показать курс", callback_data="admin:show_rate"),
        ],
        [
            InlineKeyboardButton(text="✏️ Изменить курс", callback_data="admin:set_rate"),
        ],
        [
            InlineKeyboardButton(text="❌ Закрыть", callback_data="admin:close"),
        ],
    ]
    return InlineKeyboardMarkup(inline_keyboard=kb)


# ---- /start (можно в ЛС или в группе) ----
@main_router.message(CommandStart())
async def cmd_start(message: Message):
    await message.answer(
        "Привет! В группе я отвечаю на сообщения со словом «курс».\n"
        "Админы могут открыть /admin для управления курсом."
    )


# ---- Админ‑панель ----
@admin_router.message(Command("admin"))
async def admin_panel(message: Message):
    # проверяем, что это нужная группа ИЛИ ЛС админа (как тебе удобнее)
    if message.chat.id != GROUP_ID and message.chat.type != "private":
        return

    if message.from_user.id not in ADMINS:
        return

    await message.answer("Админ‑панель:", reply_markup=get_admin_keyboard())


@admin_router.callback_query(F.data.startswith("admin:"))
async def admin_callbacks(callback: CallbackQuery):
    global last_rate
    user_id = callback.from_user.id

    # только админы
    if user_id not in ADMINS:
        await callback.answer("Нет прав", show_alert=True)
        return

    action = callback.data.split(":", 1)[1]

    if action == "show_rate":
        text = f"Текущий курс: {last_rate}" if last_rate is not None else "Курс ещё не задан."
        await callback.message.edit_text(text, reply_markup=get_admin_keyboard())
        await callback.answer()

    elif action == "set_rate":
        waiting_for_new_rate.add(user_id)
        await callback.message.edit_text(
            "Введи новый курс числом (например: 94.5).",
            reply_markup=None
        )
        await callback.answer()

    elif action == "close":
        await callback.message.edit_text("Админ‑панель закрыта.")
        await callback.answer()


@admin_router.message(F.text)
async def admin_set_rate_message(message: Message):
    global last_rate
    user_id = message.from_user.id

    # ждём курс от админа (работает и в группе, и в ЛС)
    if user_id in ADMINS and user_id in waiting_for_new_rate:
        text = message.text.replace(",", ".")
        match = re.search(r"(\d+(\.\d+)?)", text)
        if not match:
            await message.answer("Не нашёл число. Введи ещё раз, пример: 94.5")
            return

        last_rate = float(match.group(1))
        waiting_for_new_rate.remove(user_id)

        # отправим подтверждение в том же чате
        await message.answer(
            f"Курс обновлён: {last_rate}",
            reply_markup=get_admin_keyboard() if message.chat.id == GROUP_ID else None
        )


# ---- Логика для группы: ответ на слово «курс» ----
@main_router.message(F.chat.id == GROUP_ID, F.text)
async def group_messages(message: Message):
    global last_rate
    text_lower = message.text.lower()

    # если админ пишет сообщение в группе и там есть "курс" + число — можно также обновлять курс "в одно касание"
    if message.from_user.id in ADMINS and "курс" in text_lower:
        match = re.search(r"(\d+(\.\d+)?)", message.text.replace(",", "."))
        if match:
            last_rate = float(match.group(1))
            await message.reply(f"Курс обновлён: {last_rate}")
            return

    # любой пользователь пишет "курс" — бот отвечает последним курсом
    if "курс" in text_lower:
        if last_rate is not None:
            await message.reply(f"Текущий курс: {last_rate}")
        else:
            await message.reply("Курс ещё не задан админом.")


async def main():
    if not BOT_TOKEN:
        raise RuntimeError("Не задан TELEGRAM_BOT_TOKEN")

    session = AiohttpSession()
    bot = Bot(token=BOT_TOKEN, session=session, parse_mode=ParseMode.HTML)
    dp = Dispatcher()

    dp.include_router(main_router)
    dp.include_router(admin_router)

    await dp.start_polling(bot)


if __name__ == "__main__":
    asyncio.run(main())
