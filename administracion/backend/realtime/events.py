"""Notificaciones posteriores al commit: un fallo de Redis no revierte dinero."""
import logging
from functools import partial
from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer
from django.db import transaction

logger = logging.getLogger(__name__)


def publish(group, event, data):
    try:
        async_to_sync(get_channel_layer().group_send)(group, {
            "type": "push.event", "event": event, "data": data,
        })
    except Exception:
        logger.exception("No se pudo publicar %s en %s; el cliente debe resincronizar.", event, group)


def after_commit(group, event, data):
    transaction.on_commit(partial(publish, group, event, data))
