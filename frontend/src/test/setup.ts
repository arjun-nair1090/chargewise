import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => {
  cleanup()
})

// jsdom has no layout, so it omits scrollIntoView; tests spy on this no-op when scrolling matters.
Element.prototype.scrollIntoView = function scrollIntoView() {}
