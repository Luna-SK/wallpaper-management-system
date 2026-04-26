import { onBeforeUnmount, toValue, watch, type MaybeRefOrGetter } from 'vue'

type SubmitHandler = () => void | Promise<void>

interface DialogEnterSubmitOptions {
  disabled?: () => boolean
  allowExternalTarget?: boolean
}

const SKIP_SELECTOR = [
  'button',
  'a[href]',
  'textarea',
  '[contenteditable]',
  '[role="button"]',
  '.el-button',
  '.el-upload',
  '.el-switch',
  '.el-checkbox',
  '.el-radio',
  '.el-radio-button',
].join(',')

const BLOCKED_INPUT_TYPES = new Set(['button', 'checkbox', 'color', 'file', 'hidden', 'image', 'radio', 'range', 'reset', 'submit'])

export function dialogEnterSubmit(handler: SubmitHandler, options: DialogEnterSubmitOptions = {}) {
  let submitting = false
  return async (event: Event) => {
    if (!(event instanceof KeyboardEvent) || !shouldHandleDialogEnter(event, options)) {
      return
    }
    event.preventDefault()
    if (submitting || options.disabled?.()) {
      return
    }
    submitting = true
    try {
      await handler()
    } finally {
      submitting = false
    }
  }
}

export function useDialogEnterSubmit(active: MaybeRefOrGetter<boolean>, handler: SubmitHandler, options: DialogEnterSubmitOptions = {}) {
  const submit = dialogEnterSubmit(handler, { ...options, allowExternalTarget: true })
  let listening = false
  const listener = (event: KeyboardEvent) => {
    if (toValue(active)) {
      void submit(event)
    }
  }

  function addListener() {
    if (listening) return
    window.addEventListener('keydown', listener)
    listening = true
  }

  function removeListener() {
    if (!listening) return
    window.removeEventListener('keydown', listener)
    listening = false
  }

  const stop = watch(() => toValue(active), (visible) => {
    if (visible) {
      addListener()
    } else {
      removeListener()
    }
  }, { immediate: true })

  onBeforeUnmount(() => {
    stop()
    removeListener()
  })
}

export function shouldHandleDialogEnter(event: KeyboardEvent, options: DialogEnterSubmitOptions = {}) {
  if (event.key !== 'Enter' || event.repeat || event.isComposing || event.shiftKey || event.ctrlKey || event.altKey || event.metaKey) {
    return false
  }
  const target = event.target
  if (!(target instanceof HTMLElement)) {
    return !hasActiveMessageBox() && !hasOpenSelect()
  }
  if (hasActiveMessageBox() || hasOpenSelect(target)) {
    return false
  }
  if (options.allowExternalTarget && !target.closest('.el-dialog')) {
    return true
  }
  if (target.isContentEditable || target.closest(SKIP_SELECTOR)) {
    return false
  }
  if (target instanceof HTMLInputElement && BLOCKED_INPUT_TYPES.has(target.type)) {
    return false
  }
  return true
}

function hasOpenSelect(target?: HTMLElement) {
  return Boolean(target?.closest('.el-select')?.querySelector('[aria-expanded="true"]') || document.querySelector('.el-select [aria-expanded="true"]'))
}

function hasActiveMessageBox() {
  return Boolean(document.querySelector('.el-message-box'))
}
