import assert from 'node:assert/strict'
import test from 'node:test'
import { normalizeStreamEvent } from './chatStream.ts'

test('drops malformed payloads', () => {
  assert.equal(normalizeStreamEvent(null), null)
  assert.equal(normalizeStreamEvent('answer'), null)
  assert.equal(normalizeStreamEvent([]), null)
  assert.equal(normalizeStreamEvent({ content: 'x', done: true }), null)
})

test('normalizes a known response type', () => {
  const event = normalizeStreamEvent({
    id: 7,
    responseType: 'answer',
    content: 42,
    done: 1,
    data: { eventId: 'e1' },
    sessionId: 9,
  })

  assert.deepEqual(event, {
    id: '7',
    responseType: 'answer',
    rawResponseType: 'answer',
    content: '42',
    done: true,
    data: { eventId: 'e1' },
    sessionId: '9',
    assistantMessageId: undefined,
    requestId: undefined,
  })
})

test('accepts snake_case response_type for older payloads', () => {
  const event = normalizeStreamEvent({ response_type: 'thinking', content: 't' })
  assert.equal(event?.responseType, 'thinking')
  assert.equal(event?.rawResponseType, 'thinking')
})

test('unknown response types stay addressable instead of leaking through as known', () => {
  const event = normalizeStreamEvent({ responseType: 'brandNewEvent', id: 'x' })
  assert.equal(event?.responseType, 'unknown')
  assert.equal(event?.rawResponseType, 'brandNewEvent')
  assert.equal(event?.id, 'x')
})

test('array data is not treated as an object payload', () => {
  const event = normalizeStreamEvent({ responseType: 'references', data: [1, 2] })
  assert.equal(event?.data, undefined)
})
