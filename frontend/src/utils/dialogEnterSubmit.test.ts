import { describe, expect, it, vi } from 'vitest'
import { dialogEnterSubmit } from './dialogEnterSubmit'

function dispatchEnter(target: HTMLElement, init: KeyboardEventInit = {}) {
  const event = new KeyboardEvent('keydown', {
    key: 'Enter',
    bubbles: true,
    cancelable: true,
    ...init,
  })
  target.dispatchEvent(event)
  return event
}

describe('dialogEnterSubmit', () => {
  it('submits from a single-line input', () => {
    const input = document.createElement('input')
    const submit = vi.fn()
    input.addEventListener('keydown', dialogEnterSubmit(submit))

    const event = dispatchEnter(input)

    expect(submit).toHaveBeenCalledOnce()
    expect(event.defaultPrevented).toBe(true)
  })

  it('skips textarea, buttons, checkboxes, upload controls, and opened selects', () => {
    const submit = vi.fn()
    const handler = dialogEnterSubmit(submit)
    const textarea = document.createElement('textarea')
    const button = document.createElement('button')
    const checkbox = document.createElement('input')
    const upload = document.createElement('div')
    const select = document.createElement('div')
    const selectInput = document.createElement('input')

    checkbox.type = 'checkbox'
    upload.className = 'el-upload'
    select.className = 'el-select'
    selectInput.setAttribute('aria-expanded', 'true')
    select.append(selectInput)

    for (const target of [textarea, button, checkbox, upload, selectInput]) {
      target.addEventListener('keydown', handler)
      dispatchEnter(target)
    }

    expect(submit).not.toHaveBeenCalled()
  })

  it('ignores composition, modified, and repeated enter events', () => {
    const input = document.createElement('input')
    const submit = vi.fn()
    input.addEventListener('keydown', dialogEnterSubmit(submit))

    dispatchEnter(input, { isComposing: true })
    dispatchEnter(input, { ctrlKey: true })
    dispatchEnter(input, { repeat: true })

    expect(submit).not.toHaveBeenCalled()
  })

  it('prevents duplicate submissions while the handler is pending', async () => {
    const input = document.createElement('input')
    let finishSubmit: () => void = () => undefined
    const pending = new Promise<void>((resolve) => {
      finishSubmit = resolve
    })
    const submit = vi.fn(() => pending)
    input.addEventListener('keydown', dialogEnterSubmit(submit))

    dispatchEnter(input)
    dispatchEnter(input)
    finishSubmit()
    await pending

    expect(submit).toHaveBeenCalledOnce()
  })

  it('submits when the dialog is visible but focus is still outside the dialog', () => {
    const triggerButton = document.createElement('button')
    const submit = vi.fn()
    triggerButton.addEventListener('keydown', dialogEnterSubmit(submit, { allowExternalTarget: true }))

    const event = dispatchEnter(triggerButton)

    expect(submit).toHaveBeenCalledOnce()
    expect(event.defaultPrevented).toBe(true)
  })

  it('skips global submit while any select dropdown is open', () => {
    const body = document.body
    const select = document.createElement('div')
    const selectInput = document.createElement('input')
    const submit = vi.fn()

    select.className = 'el-select'
    selectInput.setAttribute('aria-expanded', 'true')
    select.append(selectInput)
    body.append(select)
    body.addEventListener('keydown', dialogEnterSubmit(submit, { allowExternalTarget: true }))

    dispatchEnter(body)

    expect(submit).not.toHaveBeenCalled()
    select.remove()
  })
})
